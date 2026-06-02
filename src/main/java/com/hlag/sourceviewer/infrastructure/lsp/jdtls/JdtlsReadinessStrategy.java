package com.hlag.sourceviewer.infrastructure.lsp.jdtls;

import com.hlag.sourceviewer.application.scan.lsp.LanguageServerSession;
import com.hlag.sourceviewer.application.scan.lsp.LspProjectContext;
import com.hlag.sourceviewer.infrastructure.lsp.LspReadinessStrategy;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JDTLS-specific readiness strategy that waits for two conditions before allowing indexing to proceed:
 *
 * <ol>
 *   <li>The {@code language/status} {@code ServiceReady} event — signals that the language server
 *       has finished its basic startup phase.</li>
 *   <li>All {@code window/workDoneProgress} build jobs (Maven/Gradle classpath resolution,
 *       workspace building, etc.) have completed — signals that the semantic model is populated
 *       and hover queries will return meaningful results.</li>
 * </ol>
 *
 * <p>The quiescence check polls every {@value #QUIESCENCE_POLL_INTERVAL_MILLIS} ms and requires
 * {@value #QUIESCENCE_STABLE_CHECKS_REQUIRED} consecutive empty-queue observations
 * (i.e. at least {@value #QUIESCENCE_POLL_INTERVAL_MILLIS} ×
 * {@value #QUIESCENCE_STABLE_CHECKS_REQUIRED} ms of stability) before declaring success.
 * The total timeout covers both phases combined.</p>
 */
@ApplicationScoped
public class JdtlsReadinessStrategy implements LspReadinessStrategy {

    private static final Logger logger = LoggerFactory.getLogger(JdtlsReadinessStrategy.class);

    private static final long QUIESCENCE_POLL_INTERVAL_MILLIS = 500;
    private static final int QUIESCENCE_STABLE_CHECKS_REQUIRED = 4; // 4 × 500 ms = 2 s with empty queue

    /**
     * Waits for JDTLS {@code ServiceReady} followed by quiescence of all background build jobs.
     *
     * @throws IllegalStateException if no ready signal is present, the timeout elapses, or
     *                               JDTLS reported an error before becoming ready
     */
    @Override
    public void waitUntilReady(LanguageServerSession<?> session, LspProjectContext context, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);

        awaitServiceReady(session, deadline);
        awaitWorkDoneProgressQuiescence(session, deadline);
    }

    private static void awaitServiceReady(LanguageServerSession<?> session, Instant deadline) {
        CompletableFuture<Void> readySignal = session.readySignal()
                .orElseThrow(() -> new IllegalStateException(
                        "JDTLS session does not carry a ready-signal future — "
                                + "was the session created with JdtlsNotifyingLanguageClient?"));

        long remainingMillis = Duration.between(Instant.now(), deadline).toMillis();
        try {
            readySignal.get(Math.max(0, remainingMillis), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            throw new IllegalStateException(
                    "JDTLS did not send ServiceReady within the configured timeout", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException(
                    "JDTLS reported an error during startup", exception.getCause());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while waiting for JDTLS ServiceReady", exception);
        }
    }

    private static void awaitWorkDoneProgressQuiescence(LanguageServerSession<?> session, Instant deadline) {
        if (!(session.languageClient() instanceof JdtlsNotifyingLanguageClient jdtlsClient)) {
            return;
        }

        int stableChecks = 0;
        while (Instant.now().isBefore(deadline)) {
            Map<String, String> active = jdtlsClient.activeWorkDoneProgressSnapshot();
            if (active.isEmpty()) {
                stableChecks++;
                if (stableChecks >= QUIESCENCE_STABLE_CHECKS_REQUIRED) {
                    logger.info("JDTLS work-done-progress quiescent — all build jobs finished, proceeding");
                    return;
                }
                logger.debug(
                        "JDTLS work-done-progress empty (stable check {}/{})",
                        stableChecks,
                        QUIESCENCE_STABLE_CHECKS_REQUIRED);
            } else {
                stableChecks = 0;
                logger.debug(
                        "JDTLS waiting for {} work-done-progress job(s): {}",
                        active.size(),
                        active.values());
            }

            try {
                Thread.sleep(QUIESCENCE_POLL_INTERVAL_MILLIS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "Interrupted while waiting for JDTLS work-done-progress quiescence", exception);
            }
        }

        Map<String, String> remaining = jdtlsClient.activeWorkDoneProgressSnapshot();
        logger.warn(
                "JDTLS work-done-progress did not quiesce before timeout deadline; still active: {}",
                remaining.values());
    }
}
