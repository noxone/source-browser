package com.hlag.sourceviewer.adapter.outgoing.persistence;

import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.source.TokenDetail;
import com.hlag.sourceviewer.domain.port.outgoing.TokenDetailRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PanacheTokenDetailRepository implements TokenDetailRepository {

    private final EntityManager entityManager;

    @Inject
    public PanacheTokenDetailRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Optional<TokenDetail> findByFileAndPosition(FileIdentifier fileIdentifier, int line, int columnStart) {
        return entityManager
                .createQuery("FROM TokenDetail d WHERE d.fileIdentifier = :fid" +
                             " AND d.line = :line AND d.columnStart = :col AND d.published = true",
                        TokenDetail.class)
                .setParameter("fid", fileIdentifier)
                .setParameter("line", line)
                .setParameter("col", columnStart)
                .getResultStream()
                .findFirst();
    }

    @Override
    public List<TokenDetail> findByFile(FileIdentifier fileIdentifier) {
        return entityManager
                .createQuery("FROM TokenDetail d WHERE d.fileIdentifier = :fid AND d.published = true",
                        TokenDetail.class)
                .setParameter("fid", fileIdentifier)
                .getResultList();
    }

    @Override
    @Transactional
    public void insertUnpublished(TokenDetail tokenDetail, Long scanJobId) {
        tokenDetail.markUnpublished(scanJobId);
        entityManager.persist(tokenDetail);
    }

    @Override
    @Transactional
    public void insertAllUnpublished(List<TokenDetail> tokenDetails, Long scanJobId) {
        for (TokenDetail td : tokenDetails) {
            td.markUnpublished(scanJobId);
            entityManager.persist(td);
        }
    }

    @Override
    @Transactional
    public void publishByScanJob(Long scanJobId) {
        entityManager
                .createQuery("UPDATE TokenDetail d SET d.published = true" +
                             " WHERE d.scanJobId = :jobId AND d.published = false")
                .setParameter("jobId", scanJobId)
                .executeUpdate();
    }

    @Override
    @Transactional
    public void deleteSupersededByScanJob(Long scanJobId) {
        entityManager.createNativeQuery("""
                DELETE FROM token_detail
                WHERE  published = true
                  AND  (scan_job_id IS NULL OR scan_job_id <> :scanJobId)
                  AND  EXISTS (
                           SELECT 1 FROM token_detail td2
                           WHERE  td2.file_id     = token_detail.file_id
                             AND  td2.scan_job_id = :scanJobId
                             AND  td2.published   = true
                       )
                """)
                .setParameter("scanJobId", scanJobId)
                .executeUpdate();
    }

    @Override
    @Transactional
    public void deleteUnpublishedByScanJob(Long scanJobId) {
        entityManager
                .createQuery("DELETE FROM TokenDetail d WHERE d.scanJobId = :jobId AND d.published = false")
                .setParameter("jobId", scanJobId)
                .executeUpdate();
    }

    @Override
    public long countPublished() {
        return entityManager
                .createQuery("SELECT COUNT(d) FROM TokenDetail d WHERE d.published = true", Long.class)
                .getSingleResult();
    }
}
