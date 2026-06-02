package com.hlag.sourceviewer.infrastructure.lsp;

import com.hlag.sourceviewer.application.scan.lsp.LanguageServerSession;
import com.hlag.sourceviewer.application.scan.lsp.LspProjectContext;
import java.io.IOException;
import java.util.Optional;

/** Starts a language-specific language server process and creates an LSP session. */
public interface LanguageServerProvider {

    /** Returns the language token supported by this provider (for example {@code java}). */
    String supportedLanguage();

    /** Starts the language-server process and returns an active session. */
    LanguageServerSession startSession(LspProjectContext context) throws IOException;

    /** Returns an optional language-specific readiness strategy override. */
    default Optional<LspReadinessStrategy> readinessStrategy() {
        return Optional.empty();
    }
}

