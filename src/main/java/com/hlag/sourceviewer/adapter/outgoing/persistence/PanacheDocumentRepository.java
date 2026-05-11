package com.hlag.sourceviewer.adapter.outgoing.persistence;

import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.source.Document;
import com.hlag.sourceviewer.domain.port.outgoing.DocumentRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class PanacheDocumentRepository
        implements DocumentRepository, PanacheRepositoryBase<Document, Long> {

    @Override
    @Transactional
    public void deleteByFile(FileIdentifier fileIdentifier) {
        delete("fileIdentifier", fileIdentifier);
    }

    @Override
    @Transactional
    public void insert(Document document) {
        persist(document);
    }

    @Override
    @Transactional
    public void insertUnpublished(Document document) {
        persist(document);
    }

    @Override
    @Transactional
    public void publishByScanJob(Long scanJobId) {
        update("published = true WHERE scanJobId = ?1 AND published = false", scanJobId);
    }

    @Override
    @Transactional
    public void deleteSupersededDocuments(Long scanJobId) {
        // Delete old published documents for any file that now has a freshly published document
        // from the current scan job. The self-join identifies superseded rows without needing
        // to pass the full file-id list explicitly.
        getEntityManager().createNativeQuery("""
                DELETE FROM document d
                WHERE  d.published = true
                  AND  (d.scan_job_id IS NULL OR d.scan_job_id <> :scanJobId)
                  AND  EXISTS (
                           SELECT 1 FROM document d2
                           WHERE  d2.file_id      = d.file_id
                             AND  d2.scan_job_id  = :scanJobId
                             AND  d2.published    = true
                       )
                """)
                .setParameter("scanJobId", scanJobId)
                .executeUpdate();
    }

    @Override
    @Transactional
    public void deleteUnpublishedByScanJob(Long scanJobId) {
        delete("scanJobId = ?1 AND published = false", scanJobId);
    }
}
