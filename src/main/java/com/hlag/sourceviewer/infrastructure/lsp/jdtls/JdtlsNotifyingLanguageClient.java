package com.hlag.sourceviewer.infrastructure.lsp.jdtls;

import com.hlag.sourceviewer.infrastructure.lsp.LoggingLanguageClient;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JDTLS-specific language client that listens for {@code language/status} notifications
 * and completes a {@link CompletableFuture} when {@code ServiceReady} is received.
 *
 * <p>The {@link #serviceReadyFuture()} can be awaited by a readiness strategy without any
 * polling — the future is driven exclusively by server events. It completes exceptionally
 * if JDTLS reports an {@code Error} status before becoming ready.</p>
 */
public class JdtlsNotifyingLanguageClient extends LoggingLanguageClient implements JdtlsLanguageClient {

    private static final Logger logger = LoggerFactory.getLogger(JdtlsNotifyingLanguageClient.class);

    private static final String MESSAGE_SERVICE_READY = "ServiceReady";
    private static final String TYPE_ERROR = "Error";

    private final CompletableFuture<Void> serviceReadyFuture = new CompletableFuture<>();

    /**
     * Returns the future that completes when JDTLS reports {@code ServiceReady}, or
     * completes exceptionally if JDTLS reports an error before becoming ready.
     */
    public CompletableFuture<Void> serviceReadyFuture() {
        return serviceReadyFuture;
    }

    /** @inheritDoc */
    @Override
    public void languageStatus(JdtlsStatusReport report) {
        if (report == null) {
            return;
        }
        logger.debug("JDTLS language/status: type='{}' message='{}'", report.getType(), report.getMessage());

        if (serviceReadyFuture.isDone()) {
            return;
        }

        if (TYPE_ERROR.equalsIgnoreCase(report.getType())) {
            serviceReadyFuture.completeExceptionally(
                    new IllegalStateException(
                            "JDTLS reported an error before becoming ready: " + report.getMessage()));
            return;
        }

        if (MESSAGE_SERVICE_READY.equalsIgnoreCase(report.getMessage())) {
            logger.info("JDTLS reports ServiceReady — language server is ready");
            serviceReadyFuture.complete(null);
        }
    }
}

