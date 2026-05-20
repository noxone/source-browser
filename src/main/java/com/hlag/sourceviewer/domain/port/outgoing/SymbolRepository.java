package com.hlag.sourceviewer.domain.port.outgoing;

import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.QualifiedName;
import com.hlag.sourceviewer.domain.model.identifier.SimpleName;
import com.hlag.sourceviewer.domain.model.identifier.SymbolIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.*;
import com.hlag.sourceviewer.domain.model.source.Symbol;

import java.util.List;
import java.util.Optional;

/**
 * Port for accessing persisted symbols.
 * Implemented by {@code com.hlag.sourceviewer.adapter.outgoing.persistence.JooqSymbolRepository}.
 *
 * <p>All read methods filter {@code published=true} so callers always see a consistent
 * view — either the previous scan's symbols or the new scan's symbols, never a mix.</p>
 */
public interface SymbolRepository {

    Optional<Symbol> findByIdentifier(SymbolIdentifier identifier);

    /** Returns the published symbol with the given qualified name, or empty. */
    Optional<Symbol> findByQualifiedName(QualifiedName qualifiedName);

    /**
     * Used during scan batch processing to resolve cross-file references.
     * Prefers an unpublished symbol from {@code scanJobId} over any published symbol,
     * so references are linked to the newly inserted row that survives activation.
     */
    Optional<Symbol> findByQualifiedNameForScan(QualifiedName qualifiedName, Long scanJobId);

    List<Symbol> findBySimpleName(SimpleName name);

    List<Symbol> findByFile(FileIdentifier fileIdentifier);

    SymbolIdentifier insert(Symbol symbol);

    /** Inserts a symbol as unpublished, to be activated via {@link #publishByScanJob}. */
    SymbolIdentifier insertUnpublished(Symbol symbol, Long scanJobId);

    void deleteByFile(FileIdentifier fileIdentifier);

    /** Flips all unpublished symbols for the given scan job to published=true. */
    void publishByScanJob(Long scanJobId);

    /**
     * Deletes old published symbols whose file now has newly published symbols from
     * the given scan job. Must be called after {@link #publishByScanJob}.
     */
    void deleteSupersededByScanJob(Long scanJobId);

    /** Deletes all unpublished symbols for the given scan job (cleanup on failure). */
    void deleteUnpublishedByScanJob(Long scanJobId);

    /** Returns the total number of published symbols. */
    long countPublished();
}
