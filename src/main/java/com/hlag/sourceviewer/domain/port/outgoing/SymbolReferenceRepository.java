package com.hlag.sourceviewer.domain.port.outgoing;

import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.ReferenceIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.SymbolIdentifier;
import com.hlag.sourceviewer.domain.model.source.SymbolReference;

import java.util.List;

/**
 * Port for accessing persisted symbol references.
 *
 * <p>All read methods filter {@code published=true} so callers always see a consistent
 * view — either the previous scan's references or the new scan's references, never a mix.</p>
 */
public interface SymbolReferenceRepository {

    List<SymbolReference> findBySymbol(SymbolIdentifier symbolIdentifier);

    List<SymbolReference> findByFile(FileIdentifier fileIdentifier);

    ReferenceIdentifier insert(SymbolReference symbolReference);

    /** Inserts a reference as unpublished, to be activated via {@link #publishByScanJob}. */
    ReferenceIdentifier insertUnpublished(SymbolReference symbolReference, Long scanJobId);

    void deleteByFile(FileIdentifier fileIdentifier);

    /** Flips all unpublished references for the given scan job to published=true. */
    void publishByScanJob(Long scanJobId);

    /**
     * Deletes old published references whose file now has newly published references from
     * the given scan job. Must be called after {@link #publishByScanJob}.
     */
    void deleteSupersededByScanJob(Long scanJobId);

    /** Deletes all unpublished references for the given scan job (cleanup on failure). */
    void deleteUnpublishedByScanJob(Long scanJobId);

    /**
     * Returns unpublished references for the given file written during the specified scan job,
     * falling back to published references when no unpublished ones exist yet.
     * Used during token-stream enrichment to attach symbol IDs to identifier tokens.
     */
    List<SymbolReference> findByFileForScan(FileIdentifier fileIdentifier, Long scanJobId);

    /** Returns the total number of published references. */
    long countPublished();
}
