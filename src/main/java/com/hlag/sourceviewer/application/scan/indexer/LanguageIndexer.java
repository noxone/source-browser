package com.hlag.sourceviewer.application.scan.indexer;

import com.hlag.sourceviewer.application.scan.ParsedFile;
import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import java.nio.file.Path;
import java.util.List;

/**
 * Strategy for language-specific symbol indexing.
 *
 * <p>Implementations must be {@code @ApplicationScoped} CDI beans. The context object
 * produced by {@link #prepare} is an opaque per-scan value passed back into
 * {@link #indexFile} — each implementation defines what it carries. Implementations must
 * be stateless; all scan-specific state is carried in that context object.</p>
 *
 * <p>Selection rule: for each language, the indexer with the lowest {@link #priority()}
 * value whose {@link #analyze} returns {@code true} is used for the scan.</p>
 */
public interface LanguageIndexer {

    /** Returns the language token this indexer handles (e.g. {@code "java"}). */
    String supportedLanguage();

    /**
     * Priority for disambiguation when multiple indexers support the same language.
     * Lower value wins. Convention: specific implementations use 10, generic fallbacks use 100.
     */
    int priority();

    /**
     * Determines whether this indexer is applicable to the given repository.
     *
     * @param repoRoot local filesystem path of the checked-out repository
     * @param allFiles all file paths that will be indexed in this scan
     * @return {@code true} if this indexer should be used
     */
    boolean analyze(Path repoRoot, List<FilePath> allFiles);

    /**
     * Prepares resources needed for indexing (e.g. builds a TypeSolver, resolves Maven
     * artifacts). Called once per scan before any {@link #indexFile} calls.
     *
     * @param repoRoot local filesystem path of the checked-out repository
     * @return an opaque context object carrying all per-scan state; passed back to {@link #indexFile}
     */
    Object prepare(Path repoRoot);

    /** Returns {@code true} if this indexer can process the given file path. */
    boolean handles(FilePath path);

    /**
     * Indexes a single source file and returns its symbol declarations and references.
     *
     * @param fileId   the persisted identifier of the source file
     * @param path     the repository-relative path
     * @param content  the raw file content (UTF-8 string from git)
     * @param context  the context produced by {@link #prepare} for this scan
     * @return parsed declarations and pending references
     */
    ParsedFile indexFile(FileIdentifier fileId, FilePath path, String content, Object context);

    /**
     * Releases any resources held by the context object after a scan completes.
     *
     * <p>The default implementation is a no-op. Implementations that hold expensive
     * resources (e.g. open file handles) may override this. Note that LSP server
     * processes are <em>not</em> shut down here — they remain alive for interactive
     * hover queries after the scan and are managed by the {@code LspServerManager}.</p>
     *
     * @param context the context produced by {@link #prepare} for this scan
     */
    default void cleanup(Object context) {}
}
