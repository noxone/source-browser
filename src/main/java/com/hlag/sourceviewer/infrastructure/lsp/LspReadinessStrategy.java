package com.hlag.sourceviewer.infrastructure.lsp;

import com.hlag.sourceviewer.application.scan.lsp.LanguageServerSession;
import com.hlag.sourceviewer.application.scan.lsp.LspProjectContext;
import java.time.Duration;

/** Waits until a started language-server session can be used for requests. */
public interface LspReadinessStrategy {

    /** Waits for readiness or throws if the timeout is reached. */
    void waitUntilReady(LanguageServerSession session, LspProjectContext context, Duration timeout);
}

