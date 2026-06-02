package com.hlag.sourceviewer.domain.port.outgoing;

import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.search.DocumentSearchMatch;
import com.hlag.sourceviewer.domain.model.source.Document;

import java.util.List;

/**
 * Port for persisting full-text search documents.
 */
public interface DocumentRepository {

    /**
     * Performs a full-text search and returns ranked matches.
     *
     * @param repositoryIds empty list means "all repositories"
     * @param fileFilter    null means no file filter; otherwise a raw filter string (glob/regex/negation)
     */
    List<DocumentSearchMatch> search(String text, List<Long> repositoryIds, int maxResults, int offset, String fileFilter);

    /** Removes all document entries for the given file. */
    void deleteByFile(FileIdentifier fileIdentifier);

    /** Persists a new document entry as published immediately. */
    void insert(Document document);

    /** Persists a document as unpublished (published=false); activated later via {@link #publishByScanJob}. */
    void insertUnpublished(Document document);

    /** Flips all unpublished documents for the given scan job to published=true. */
    void publishByScanJob(Long scanJobId);

    /**
     * Deletes old published documents whose file now has a newly published document from
     * the given scan job. Must be called after {@link #publishByScanJob}.
     */
    void deleteSupersededDocuments(Long scanJobId);

    /** Deletes all unpublished documents for the given scan job (cleanup on failure). */
    void deleteUnpublishedByScanJob(Long scanJobId);

    /** Returns the total number of published documents. */
    long countPublished();
}
