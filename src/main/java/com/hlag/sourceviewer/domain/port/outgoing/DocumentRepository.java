package com.hlag.sourceviewer.domain.port.outgoing;

import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.search.DocumentSearchMatch;
import com.hlag.sourceviewer.domain.model.source.Document;

import java.util.List;

/**
 * Port for persisting full-text search documents.
 */
public interface DocumentRepository {

    /** Performs a full-text search and returns ranked matches. */
    List<DocumentSearchMatch> search(String text, int maxResults, int offset);

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
}
