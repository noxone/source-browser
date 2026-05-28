package com.hlag.sourceviewer.application.scan.lsp;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

/** Represents an active language-server session. */
public interface LanguageServerSession extends AutoCloseable {

    /** Returns the language token for this session. */
    String language();

    /** Returns the project root served by this session. */
    Path projectRoot();

    /** Returns the persistent workspace directory used by this session. */
    Path workspacePath();

    /** Returns the LSP remote proxy. */
    LanguageServer languageServer();

    /** Returns the text-document service proxy. */
    TextDocumentService textDocumentService();

    /** Returns the workspace service proxy. */
    WorkspaceService workspaceService();

    /**
     * Returns an optional future that completes when the server signals it is ready.
     *
     * <p>Language-server providers that support explicit readiness events populate this
     * future from server notifications. The default returns empty, which tells the
     * readiness strategy to fall back to its own probe mechanism.</p>
     */
    default Optional<CompletableFuture<Void>> readySignal() {
        return Optional.empty();
    }

    /** @inheritDoc */
    @Override
    void close();
}

