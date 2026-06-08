package com.hlag.sourceviewer.infrastructure.scheduler;

import com.hlag.sourceviewer.domain.model.identifier.CommitSha;
import com.hlag.sourceviewer.domain.model.setting.SettingChangedEvent;
import com.hlag.sourceviewer.domain.model.source.ScanJob;
import com.hlag.sourceviewer.domain.port.incoming.ManageAppSettingsUseCase;
import com.hlag.sourceviewer.domain.port.incoming.ManageGitProviderGroupsUseCase;
import com.hlag.sourceviewer.domain.port.incoming.ScanRepositoryUseCase;
import com.hlag.sourceviewer.domain.port.incoming.SyncGroupRepositoriesUseCase;
import com.hlag.sourceviewer.domain.port.outgoing.GitAccess;
import com.hlag.sourceviewer.domain.port.outgoing.RepositoryStore;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import io.quarkus.scheduler.ScheduledExecution;
import io.quarkus.scheduler.Scheduler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.function.Consumer;

import static com.hlag.sourceviewer.domain.port.incoming.ManageAppSettingsUseCase.*;

/**
 * Cron-based scan trigger as a safety net for missed webhooks.
 *
 * <p>Checks every hour (configurable) whether the HEAD commit of a repository has
 * changed compared to the last known commit and queues a new scan job if needed.
 * Both cron expressions can be changed at runtime via the admin settings UI.</p>
 */
@ApplicationScoped
public class ScanScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ScanScheduler.class);

    private static final String IDENTITY_SYNC_GROUPS = "scan-scheduler.sync-groups";
    private static final String IDENTITY_DETECT_CHANGES = "scan-scheduler.detect-changes";

    private final RepositoryStore repositoryStore;
    private final GitAccess gitAccess;
    private final ScanRepositoryUseCase scanRepositoryUseCase;
    private final ManageGitProviderGroupsUseCase manageGitProviderGroupsUseCase;
    private final SyncGroupRepositoriesUseCase syncGroupRepositoriesUseCase;
    private final ManageAppSettingsUseCase manageAppSettingsUseCase;
    private final Scheduler scheduler;

    @Inject
    public ScanScheduler(
            RepositoryStore repositoryStore,
            GitAccess gitAccess,
            ScanRepositoryUseCase scanRepositoryUseCase,
            ManageGitProviderGroupsUseCase manageGitProviderGroupsUseCase,
            SyncGroupRepositoriesUseCase syncGroupRepositoriesUseCase,
            ManageAppSettingsUseCase manageAppSettingsUseCase,
            Scheduler scheduler) {
        this.repositoryStore = repositoryStore;
        this.gitAccess = gitAccess;
        this.scanRepositoryUseCase = scanRepositoryUseCase;
        this.manageGitProviderGroupsUseCase = manageGitProviderGroupsUseCase;
        this.syncGroupRepositoriesUseCase = syncGroupRepositoriesUseCase;
        this.manageAppSettingsUseCase = manageAppSettingsUseCase;
        this.scheduler = scheduler;
    }

    void onStart(@Observes StartupEvent event) {
        if (!scheduler.isStarted()) {
            logger.info("Scheduler not started, skipping dynamic job setup");
            return;
        }
        String syncCron = manageAppSettingsUseCase.getSetting(
                SETTING_SCHEDULER_SYNC_GROUPS_CRON, DEFAULT_SCHEDULER_SYNC_GROUPS_CRON);
        String detectCron = manageAppSettingsUseCase.getSetting(
                SETTING_SCHEDULER_DETECT_CHANGES_CRON, DEFAULT_SCHEDULER_DETECT_CHANGES_CRON);

        scheduleJob(IDENTITY_SYNC_GROUPS, syncCron, ex -> syncAllGroups());
        scheduleJob(IDENTITY_DETECT_CHANGES, detectCron, ex -> detectChangesInAllRepositories());

        logger.info("Scheduled '{}' with cron '{}'", IDENTITY_SYNC_GROUPS, syncCron);
        logger.info("Scheduled '{}' with cron '{}'", IDENTITY_DETECT_CHANGES, detectCron);
    }

    void onSettingChanged(@Observes(during = TransactionPhase.AFTER_SUCCESS) SettingChangedEvent event) {
        if (SETTING_SCHEDULER_SYNC_GROUPS_CRON.equals(event.key())) {
            rescheduleJob(IDENTITY_SYNC_GROUPS, event.value(), ex -> syncAllGroups());
        } else if (SETTING_SCHEDULER_DETECT_CHANGES_CRON.equals(event.key())) {
            rescheduleJob(IDENTITY_DETECT_CHANGES, event.value(), ex -> detectChangesInAllRepositories());
        }
    }

    private void scheduleJob(String identity, String cron, Consumer<ScheduledExecution> task) {
        scheduler.newJob(identity)
                .setCron(cron)
                .setConcurrentExecution(ConcurrentExecution.SKIP)
                .setTask(task)
                .schedule();
    }

    private void rescheduleJob(String identity, String newCron, Consumer<ScheduledExecution> task) {
        scheduler.unscheduleJob(identity);
        scheduleJob(identity, newCron, task);
        logger.info("Job '{}' rescheduled with cron '{}'", identity, newCron);
        // TODO: Check new cron expression before using, if it is OK
    }

    public void syncAllGroups() {
        logger.info("Daily group sync started");
        manageGitProviderGroupsUseCase.listGitProviderGroups().forEach(group -> {
            try {
                syncGroupRepositoriesUseCase.syncGroup(group.identifier());
            } catch (Exception e) {
                logger.error("Daily group sync failed for group '{}': {}",
                        group.groupPath().value(), e.getMessage(), e);
            }
        });
    }

    public void detectChangesInAllRepositories() {
        logger.debug("Cron scan started");

        repositoryStore.findAll().forEach(repository -> {
            try {
                if (!gitAccess.localRepositoryExists(repository)) {
                    logger.info("Repository '{}' not yet cloned locally, scheduling initial scan",
                            repository.name().value());
                    scanRepositoryUseCase.enqueueScan(
                            new ScanRepositoryUseCase.ScanCommand(
                                    repository.identifier(),
                                    Optional.empty(),
                                    ScanJob.TriggerType.CRON,
                                    false
                            ));
                    return;
                }

                CommitSha remoteHeadSha = gitAccess.fetchRemoteHeadSha(
                        repository, repository.defaultBranch());

                boolean hasNewCommit = repository.lastCommitSha()
                        .map(knownSha -> !knownSha.value().equals(remoteHeadSha.value()))
                        .orElse(true);

                if (hasNewCommit) {
                    logger.info("New commit detected in repository {}: {}",
                            repository.name().value(),
                            remoteHeadSha.shortForm());

                    scanRepositoryUseCase.enqueueScan(
                            new ScanRepositoryUseCase.ScanCommand(
                                    repository.identifier(),
                                    Optional.of(remoteHeadSha),
                                    ScanJob.TriggerType.CRON,
                                    false
                            ));
                }
            } catch (Exception exception) {
                logger.error("Error during cron scan for repository {}: {}",
                        repository.name().value(), exception.getMessage(), exception);
            }
        });
    }
}
