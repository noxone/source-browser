package com.hlag.sourceviewer.application.scan.lsp;

import org.eclipse.lsp4j.services.LanguageClient;

/** Starts language servers for repository indexing and returns ready-to-use sessions. */
public interface LspManager {

    /**
     * Starts the language server for {@code language}, waits until it is ready,
     * and returns a session object that allows LSP communication.
     */
    <C extends LanguageClient> LanguageServerSession<C> getLspForLanguage(String language, LspProjectContext context);
}

