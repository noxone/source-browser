package com.hlag.sourceviewer.adapter.outgoing.persistence;

import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.ReferenceIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.SymbolIdentifier;
import com.hlag.sourceviewer.domain.model.source.SymbolReference;
import com.hlag.sourceviewer.domain.port.outgoing.SymbolReferenceRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class PanacheSymbolReferenceRepository implements SymbolReferenceRepository {

    private final EntityManager entityManager;

    @Inject
    public PanacheSymbolReferenceRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<SymbolReference> findBySymbol(SymbolIdentifier symbolIdentifier) {
        return entityManager
                .createQuery("FROM SymbolReference r WHERE r.symbolIdentifier = :id AND r.published = true",
                        SymbolReference.class)
                .setParameter("id", symbolIdentifier)
                .getResultList();
    }

    @Override
    public List<SymbolReference> findByFile(FileIdentifier fileIdentifier) {
        return entityManager
                .createQuery("FROM SymbolReference r WHERE r.fileIdentifier = :id AND r.published = true",
                        SymbolReference.class)
                .setParameter("id", fileIdentifier)
                .getResultList();
    }

    @Override
    @Transactional
    public ReferenceIdentifier insert(SymbolReference symbolReference) {
        entityManager.persist(symbolReference);
        return symbolReference.identifier();
    }

    @Override
    @Transactional
    public ReferenceIdentifier insertUnpublished(SymbolReference symbolReference, Long scanJobId) {
        symbolReference.markUnpublished(scanJobId);
        entityManager.persist(symbolReference);
        return symbolReference.identifier();
    }

    @Override
    @Transactional
    public void deleteByFile(FileIdentifier fileIdentifier) {
        entityManager
                .createQuery("DELETE FROM SymbolReference r WHERE r.fileIdentifier = :id")
                .setParameter("id", fileIdentifier)
                .executeUpdate();
    }

    @Override
    @Transactional
    public void publishByScanJob(Long scanJobId) {
        entityManager
                .createQuery("UPDATE SymbolReference r SET r.published = true WHERE r.scanJobId = :jobId AND r.published = false")
                .setParameter("jobId", scanJobId)
                .executeUpdate();
    }

    @Override
    @Transactional
    public void deleteSupersededByScanJob(Long scanJobId) {
        entityManager.createNativeQuery("""
                DELETE FROM reference
                WHERE  published = true
                  AND  (scan_job_id IS NULL OR scan_job_id <> :scanJobId)
                  AND  EXISTS (
                           SELECT 1 FROM reference r2
                           WHERE  r2.file_id     = reference.file_id
                             AND  r2.scan_job_id = :scanJobId
                             AND  r2.published   = true
                       )
                """)
                .setParameter("scanJobId", scanJobId)
                .executeUpdate();
    }

    @Override
    @Transactional
    public void deleteUnpublishedByScanJob(Long scanJobId) {
        entityManager
                .createQuery("DELETE FROM SymbolReference r WHERE r.scanJobId = :jobId AND r.published = false")
                .setParameter("jobId", scanJobId)
                .executeUpdate();
    }

    @Override
    public List<SymbolReference> findByFileForScan(FileIdentifier fileIdentifier, Long scanJobId) {
        List<SymbolReference> unpublished = entityManager
                .createQuery("FROM SymbolReference r WHERE r.fileIdentifier = :id" +
                             " AND r.published = false AND r.scanJobId = :jobId",
                        SymbolReference.class)
                .setParameter("id", fileIdentifier)
                .setParameter("jobId", scanJobId)
                .getResultList();
        if (!unpublished.isEmpty()) {
            return unpublished;
        }
        return findByFile(fileIdentifier);
    }
}
