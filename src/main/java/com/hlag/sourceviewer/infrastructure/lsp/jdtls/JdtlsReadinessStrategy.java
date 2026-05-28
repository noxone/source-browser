package com.hlag.sourceviewer.infrastructure.lsp.jdtls;

import com.hlag.sourceviewer.application.scan.lsp.LanguageServerSession;
import com.hlag.sourceviewer.application.scan.lsp.LspProjectContext;
import com.hlag.sourceviewer.infrastructure.lsp.LspReadinessStrategy;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * JDTLS-specific readiness strategy that blocks on the {@code language/status}
 * {@code ServiceReady} event rather than polling.
 *
 * <p>The session's {@link LanguageServerSession#readySignal()} future is populated by
 * {@link JdtlsNotifyingLanguageClient} the moment JDTLS dispatches its service-ready
 * notification. This strategy simply awaits that future — no polling, no sleep loops.
 * The timeout is a hard upper bound for the case where JDTLS never sends the notification.</p>
 */
@ApplicationScoped
public class JdtlsReadinessStrategy implements LspReadinessStrategy {

    /**
     * Waits for the JDTLS {@code ServiceReady} event using the session's ready-signal future.
     *
     * @throws IllegalStateException if no ready signal is present, the timeout elapses, or
     *                               JDTLS reported an error before becoming ready
     */
    @Override
    public void waitUntilReady(LanguageServerSession session, LspProjectContext context, Duration timeout) {
        CompletableFuture<Void> readySignal = session.readySignal()
                .orElseThrow(() -> new IllegalStateException(
                        "JDTLS session does not carry a ready-signal future — "
                                + "was the session created with JdtlsNotifyingLanguageClient?"));
        try {
            readySignal.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            throw new IllegalStateException(
                    "JDTLS did not send ServiceReady within " + timeout.toMillis() + " ms", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException(
                    "JDTLS reported an error during startup", exception.getCause());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while waiting for JDTLS ServiceReady", exception);
        }
    }
}

