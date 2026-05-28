package com.hlag.sourceviewer.application.scan.lsp;

/** Starts language servers for repository indexing and returns ready-to-use sessions. */
public interface LspManager {

    /**
     * Starts the language server for {@code language}, waits until it is ready,
     * and returns a session object that allows LSP communication.
     */
    LanguageServerSession getLspForLanguage(String language, LspProjectContext context);
}

