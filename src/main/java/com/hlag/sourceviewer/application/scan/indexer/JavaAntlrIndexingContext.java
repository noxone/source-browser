package com.hlag.sourceviewer.application.scan.indexer;

import com.hlag.sourceviewer.application.scan.lsp.DiagnosticsCapable;
import com.hlag.sourceviewer.application.scan.lsp.LanguageServerSession;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;

/**
 * Per-scan context for {@link JavaAntlrIndexer}.
 *
 * <p>Carries the repository's local root path and an optional JDTLS session.
 * The session is present when JDTLS was successfully started for this scan;
 * absent when JDTLS is not configured or failed to start.</p>
 *
 * <p>Also tracks URIs that have been pre-opened in JDTLS via
 * {@link JavaAntlrIndexer#prewarm} so that {@link JavaAntlrIndexer#indexFile}
 * can skip the {@code didOpen}/{@code waitForDiagnostics} round-trip for those files.</p>
 */
public class JavaAntlrIndexingContext {

    private final Path repoRoot;
    private final Optional<LanguageServerSession<? extends DiagnosticsCapable>> session;
    private final Set<String> prewarmingUris = ConcurrentHashMap.newKeySet();

    public JavaAntlrIndexingContext(
            Path repoRoot,
            Optional<LanguageServerSession<? extends DiagnosticsCapable>> session) {
        this.repoRoot = repoRoot;
        this.session = session;
    }

    public Path repoRoot() { return repoRoot; }

    public Optional<LanguageServerSession<? extends DiagnosticsCapable>> session() { return session; }

    /** Registers a URI as pre-opened in JDTLS (called by {@link JavaAntlrIndexer#prewarm}). */
    public void registerPrewarmed(String uri) {
        prewarmingUris.add(uri);
    }

    /**
     * Atomically checks and removes a URI from the pre-warm set.
     * Returns {@code true} when the file was pre-opened and {@code indexFile} should skip
     * the normal {@code waitForDiagnostics + didOpen} sequence.
     */
    public boolean claimPrewarmed(String uri) {
        return prewarmingUris.remove(uri);
    }

    /**
     * Closes all remaining pre-warmed files that were never processed.
     * Called during {@link JavaAntlrIndexer#teardown} to prevent JDTLS from holding
     * open documents after the scan completes.
     */
    public void closeAllPrewarmed() {
        session.ifPresent(s -> {
            for (String uri : prewarmingUris) {
                try {
                    s.textDocumentService().didClose(
                            new DidCloseTextDocumentParams(new TextDocumentIdentifier(uri)));
                } catch (Exception ignored) {}
            }
            prewarmingUris.clear();
        });
    }

    /** Closes all pre-warmed files and then the JDTLS session itself. */
    public void close() {
        closeAllPrewarmed();
        session.ifPresent(LanguageServerSession::close);
    }
}
