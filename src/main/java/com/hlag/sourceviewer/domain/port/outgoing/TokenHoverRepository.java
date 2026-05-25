package com.hlag.sourceviewer.domain.port.outgoing;

import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.source.TokenHoverEntry;

import java.util.List;
import java.util.Optional;

/**
 * Port for persisting and retrieving per-token LSP hover data.
 *
 * <p>Uses the same two-phase (unpublished → published) activation pattern as
 * {@link TokenStreamRepository} so hover data becomes visible atomically with the
 * token stream at the end of a scan.</p>
 */
public interface TokenHoverRepository {

    /** Persists the given hover entries as unpublished. Activated via {@link #publishByScanJob}. */
    void storeUnpublished(FileIdentifier fileId, List<TokenHoverEntry> entries, Long scanJobId);

    /** Flips all unpublished hover entries for the given scan job to published=true. */
    void publishByScanJob(Long scanJobId);

    /**
     * Deletes old published hover entries whose file now has newly published entries
     * from the given scan job. Must be called after {@link #publishByScanJob}.
     */
    void deleteSupersededByScanJob(Long scanJobId);

    /** Deletes all unpublished hover entries for the given scan job (cleanup on failure). */
    void deleteUnpublishedByScanJob(Long scanJobId);

    /**
     * Returns the published hover entry for the given file at the exact token position,
     * or empty if no hover data was collected for that position.
     * Both {@code line} and {@code col} are 1-based.
     */
    Optional<TokenHoverEntry> findByFileAndPosition(FileIdentifier fileId, int line, int col);
}
