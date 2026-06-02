package com.hlag.sourceviewer.infrastructure.lsp;

import com.hlag.sourceviewer.application.scan.lsp.LanguageServerSession;
import com.hlag.sourceviewer.infrastructure.lsp.jdtls.JdtlsNotifyingLanguageClient;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Language-server session backed by an external process launched via stdio transport. */
public class ProcessBackedLanguageServerSession<C extends LanguageClient> implements LanguageServerSession<C> {

    private static final Logger logger = LoggerFactory.getLogger(ProcessBackedLanguageServerSession.class);

    private final String language;
    private final Path projectRoot;
    private final Path workspacePath;
    private final LanguageServer languageServer;
    private final Process process;
    private final Future<?> listeningFuture;
    private final Optional<CompletableFuture<Void>> readySignal;
    private final C languageClient;

    public ProcessBackedLanguageServerSession(
            String language,
            Path projectRoot,
            Path workspacePath,
            LanguageServer languageServer,
            Process process,
            Future<?> listeningFuture,
            Optional<CompletableFuture<Void>> readySignal,
            C languageClient) {
        this.language = language;
        this.projectRoot = projectRoot;
        this.workspacePath = workspacePath;
        this.languageServer = languageServer;
        this.process = process;
        this.listeningFuture = listeningFuture;
        this.readySignal = readySignal;
        this.languageClient = languageClient;
    }

    @Override
    public String language() {
        return language;
    }

    @Override
    public Path projectRoot() {
        return projectRoot;
    }

    @Override
    public Path workspacePath() {
        return workspacePath;
    }

    @Override
    public LanguageServer languageServer() {
        return languageServer;
    }

    @Override
    public TextDocumentService textDocumentService() {
        return languageServer.getTextDocumentService();
    }

    @Override
    public WorkspaceService workspaceService() {
        return languageServer.getWorkspaceService();
    }

    /** @inheritDoc */
    @Override
    public Optional<CompletableFuture<Void>> readySignal() {
        return readySignal;
    }

    @Override
    public C languageClient() {
        return languageClient;
    }

    /** @inheritDoc */
    @Override
    public void close() {
        try {
            languageServer.shutdown().get();
            languageServer.exit();
        } catch (Exception exception) {
            logger.debug("LSP shutdown request failed: {}", exception.getMessage());
        }

        process.destroy();
        try {
            if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }

        if (listeningFuture != null) {
            listeningFuture.cancel(true);
        }
    }


}

