package com.hlag.sourceviewer.infrastructure.lsp;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.ProgressParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Minimal LanguageClient implementation used by background indexing sessions. */
public class LoggingLanguageClient implements LanguageClient {

    private static final Logger logger = LoggerFactory.getLogger(LoggingLanguageClient.class);

    private final AtomicReference<Instant> lastObservedServerActivity = new AtomicReference<>();

    public Instant lastObservedServerActivity() {
        return lastObservedServerActivity.get();
    }

    @Override
    public void telemetryEvent(Object object) {
        markActivity();
    }

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
        markActivity();
    }

    @Override
    public void showMessage(MessageParams messageParams) {
        markActivity();
        logger.debug("LSP message [{}]: {}", messageParams.getType(), messageParams.getMessage());
    }

    @Override
    public java.util.concurrent.CompletableFuture<org.eclipse.lsp4j.MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
        markActivity();
        logger.debug("LSP showMessageRequest [{}]: {}", requestParams.getType(), requestParams.getMessage());
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    @Override
    public void logMessage(MessageParams message) {
        markActivity();
        logger.debug("LSP log [{}]: {}", message.getType(), message.getMessage());
    }

    @Override
    public void notifyProgress(ProgressParams params) {
        markActivity();
    }

    private void markActivity() {
        lastObservedServerActivity.set(Instant.now());
    }
}

