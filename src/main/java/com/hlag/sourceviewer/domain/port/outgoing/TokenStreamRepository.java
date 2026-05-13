package com.hlag.sourceviewer.domain.port.outgoing;

import com.hlag.sourceviewer.domain.model.source.ExtractedToken;
import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.source.TokenStream;

import java.util.List;
import java.util.Optional;

/**
 * Port for persisting token streams.
 *
 * <p>The {@link #storeUnpublished} method accepts raw {@link ExtractedToken} objects
 * and handles serialization and gzip-compression internally so that the application
 * layer has no dependency on serialization libraries.</p>
 */
public interface TokenStreamRepository {

    /**
     * Serializes, gzip-compresses, and persists a token stream as unpublished.
     * Activated later via {@link #publishByScanJob}.
     */
    void storeUnpublished(FileIdentifier fileIdentifier, List<ExtractedToken> tokens, Long scanJobId);

    /** Flips all unpublished token streams for the given scan job to published=true. */
    void publishByScanJob(Long scanJobId);

    /**
     * Deletes old published token streams whose file now has a newly published token
     * stream from the given scan job. Must be called after {@link #publishByScanJob}.
     */
    void deleteSupersededByScanJob(Long scanJobId);

    /** Deletes all unpublished token streams for the given scan job (cleanup on failure). */
    void deleteUnpublishedByScanJob(Long scanJobId);

    /** Returns the published token stream for the given file, or empty if not indexed. */
    Optional<TokenStream> findByFileId(FileIdentifier fileIdentifier);
}
