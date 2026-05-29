package com.hlag.sourceviewer.application.scan.indexer;

import com.hlag.sourceviewer.application.scan.lsp.LanguageServerSession;

import com.hlag.sourceviewer.infrastructure.lsp.jdtls.JdtlsNotifyingLanguageClient;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Per-scan context for {@link JavaAntlrIndexer}.
 *
 * <p>Carries the repository's local root path and an optional JDTLS session.
 * The session is present when JDTLS was successfully started for this scan;
 * absent when JDTLS is not configured or failed to start.</p>
 */
public record JavaAntlrIndexingContext(Path repoRoot, Optional<LanguageServerSession<JdtlsNotifyingLanguageClient>> session) {

    /** Closes the JDTLS session if one is held. */
    public void close() {
        session.ifPresent(LanguageServerSession::close);
    }
}

