package com.hlag.sourceviewer.infrastructure.scheduler;

import com.hlag.sourceviewer.domain.port.incoming.ExecuteScanJobUseCase;
import com.hlag.sourceviewer.domain.port.incoming.ManageAppSettingsUseCase;
import com.hlag.sourceviewer.infrastructure.configuration.ScannerConfiguration;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dispatches queued scan jobs to a managed thread pool.
 *
 * <p>On each tick the dispatcher reads the current {@code scan.max-parallel-jobs}
 * setting from the database (so it can be changed at runtime without restarting)
 * and submits new tasks to the {@link ManagedExecutor} for every available slot.
 * Each task calls {@link ExecuteScanJobUseCase#tryExecuteNextJob()}, which atomically
 * claims one job via {@code FOR UPDATE SKIP LOCKED}.</p>
 *
 * <p>Running multiple application instances against the same database is safe:
 * the {@code FOR UPDATE SKIP LOCKED} clause ensures that each job is processed
 * by exactly one instance.</p>
 *
 * <p>The tick interval is controlled by {@code sourceviewer.scan.tick-interval}
 * in {@code application.properties} — changing it requires a restart.
 * The parallelism ({@code scan.max-parallel-jobs}) is a runtime setting stored
 * in the database and takes effect on the next tick.</p>
 */
@ApplicationScoped
public class ScanWorkerDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(ScanWorkerDispatcher.class);

    private final ExecuteScanJobUseCase executeScanJobUseCase;
    private final ManageAppSettingsUseCase manageAppSettingsUseCase;
    private final ManagedExecutor managedExecutor;
    private final ScannerConfiguration scannerConfiguration;
    private final AtomicInteger activeJobs = new AtomicInteger(0);

    @Inject
    public ScanWorkerDispatcher(
            ExecuteScanJobUseCase executeScanJobUseCase,
            ManageAppSettingsUseCase manageAppSettingsUseCase,
            ManagedExecutor managedExecutor,
            ScannerConfiguration scannerConfiguration) {
        this.executeScanJobUseCase = executeScanJobUseCase;
        this.manageAppSettingsUseCase = manageAppSettingsUseCase;
        this.managedExecutor = managedExecutor;
        this.scannerConfiguration = scannerConfiguration;
    }

    /**
     * Called on every tick. Submits scan tasks for every available parallelism slot.
     *
     * <p>The tick interval is configured via {@code sourceviewer.scan.tick-interval}
     * (default: {@code 2s}).</p>
     */
    @Scheduled(every = "${sourceviewer.scan.tick-interval:2s}")
    void dispatchPendingJobs() {
        int maxJobs = resolveMaxParallelJobs();
        int current = activeJobs.get();
        int toSubmit = Math.max(0, maxJobs - current);

        for (int i = 0; i < toSubmit; i++) {
            activeJobs.incrementAndGet();
            managedExecutor.submit(() -> {
                try {
                    executeScanJobUseCase.tryExecuteNextJob();
                } catch (Exception e) {
                    logger.error("Unexpected error in scan worker task", e);
                } finally {
                    activeJobs.decrementAndGet();
                }
            });
        }
    }

    @Scheduled(every = "1m")
    void recoverStaleJobs() {
        Instant staleBefore = Instant.now()
                .minusSeconds((long) scannerConfiguration.scanTimeoutSeconds() * 3);
        int recovered = executeScanJobUseCase.recoverStaleJobs(staleBefore);
        if (recovered > 0) {
            logger.warn("Recovered {} stale scan job(s)", recovered);
        }
    }

    private int resolveMaxParallelJobs() {
        String raw = manageAppSettingsUseCase.getSetting(
                ManageAppSettingsUseCase.SETTING_SCAN_MAX_PARALLEL_JOBS,
                ManageAppSettingsUseCase.DEFAULT_SCAN_MAX_PARALLEL_JOBS);
        if (raw == null || raw.isBlank()) {
            return Integer.parseInt(ManageAppSettingsUseCase.DEFAULT_SCAN_MAX_PARALLEL_JOBS);
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return Math.max(0, value);
        } catch (NumberFormatException e) {
            logger.warn("Invalid value for {}: '{}', using default {}",
                    ManageAppSettingsUseCase.SETTING_SCAN_MAX_PARALLEL_JOBS,
                    raw,
                    ManageAppSettingsUseCase.DEFAULT_SCAN_MAX_PARALLEL_JOBS);
            return Integer.parseInt(ManageAppSettingsUseCase.DEFAULT_SCAN_MAX_PARALLEL_JOBS);
        }
    }
}
