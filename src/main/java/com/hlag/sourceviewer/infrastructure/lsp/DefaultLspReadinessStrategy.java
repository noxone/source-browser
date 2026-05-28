package com.hlag.sourceviewer.infrastructure.lsp;

import com.hlag.sourceviewer.application.scan.lsp.LanguageServerSession;
import com.hlag.sourceviewer.application.scan.lsp.LspProjectContext;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

/**
 * Default readiness strategy.
 *
 * <p>It polls {@code workspace/symbol} as a generic, capability-driven readiness probe.
 * If the server does not support that request, the strategy waits until timeout and fails.
 * Language-specific providers can override this strategy with richer logic.</p>
 */
@ApplicationScoped
public class DefaultLspReadinessStrategy implements LspReadinessStrategy {

    private static final Duration POLL_INTERVAL = Duration.ofSeconds(1);
    private static final Duration PROBE_CALL_TIMEOUT = Duration.ofSeconds(5);

    @Override
    public void waitUntilReady(LanguageServerSession session, LspProjectContext context, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            try {
                var resultFuture = session.workspaceService().symbol(new WorkspaceSymbolParams(""));
                Either<java.util.List<? extends org.eclipse.lsp4j.SymbolInformation>,
                        java.util.List<? extends org.eclipse.lsp4j.WorkspaceSymbol>> result =
                        resultFuture.get(PROBE_CALL_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                if (result != null) {
                    return;
                }
            } catch (UnsupportedOperationException ignored) {
                // Server explicitly reports unsupported request: fall through to timeout behavior.
            } catch (Exception ignored) {
                // Startup is still in progress; keep polling until timeout.
            }
            sleep(POLL_INTERVAL);
        }
        throw new IllegalStateException("Language server did not become ready within " + timeout.toMillis() + " ms");
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for language-server readiness", interruptedException);
        }
    }
}

