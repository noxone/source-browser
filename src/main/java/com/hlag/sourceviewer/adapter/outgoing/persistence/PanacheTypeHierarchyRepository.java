package com.hlag.sourceviewer.adapter.outgoing.persistence;

import com.hlag.sourceviewer.domain.model.source.TypeHierarchyEntry;
import com.hlag.sourceviewer.domain.port.outgoing.TypeHierarchyRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class PanacheTypeHierarchyRepository implements TypeHierarchyRepository {

    private final EntityManager entityManager;

    @Inject
    public PanacheTypeHierarchyRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<TypeHierarchyEntry> findSubtypes(String supertypeFqn) {
        return entityManager
                .createQuery("FROM TypeHierarchyEntry h WHERE h.supertypeFqn = :fqn AND h.published = true",
                        TypeHierarchyEntry.class)
                .setParameter("fqn", supertypeFqn)
                .getResultList();
    }

    @Override
    public List<TypeHierarchyEntry> findSupertypes(String subtypeFqn) {
        return entityManager
                .createQuery("FROM TypeHierarchyEntry h WHERE h.subtypeFqn = :fqn AND h.published = true",
                        TypeHierarchyEntry.class)
                .setParameter("fqn", subtypeFqn)
                .getResultList();
    }

    @Override
    @Transactional
    public void insertAllUnpublished(List<TypeHierarchyEntry> entries, Long scanJobId) {
        for (TypeHierarchyEntry entry : entries) {
            entry.markUnpublished(scanJobId);
            entityManager.persist(entry);
        }
    }

    @Override
    @Transactional
    public void publishByScanJob(Long scanJobId) {
        entityManager
                .createQuery("UPDATE TypeHierarchyEntry h SET h.published = true" +
                             " WHERE h.scanJobId = :jobId AND h.published = false")
                .setParameter("jobId", scanJobId)
                .executeUpdate();
    }

    @Override
    @Transactional
    public void deleteSupersededByScanJob(Long scanJobId) {
        entityManager.createNativeQuery("""
                DELETE FROM type_hierarchy
                WHERE  published = true
                  AND  (scan_job_id IS NULL OR scan_job_id <> :scanJobId)
                  AND  EXISTS (
                           SELECT 1 FROM type_hierarchy th2
                           WHERE  th2.subtype_fqn = type_hierarchy.subtype_fqn
                             AND  th2.scan_job_id = :scanJobId
                             AND  th2.published   = true
                       )
                """)
                .setParameter("scanJobId", scanJobId)
                .executeUpdate();
    }

    @Override
    @Transactional
    public void deleteUnpublishedByScanJob(Long scanJobId) {
        entityManager
                .createQuery("DELETE FROM TypeHierarchyEntry h WHERE h.scanJobId = :jobId AND h.published = false")
                .setParameter("jobId", scanJobId)
                .executeUpdate();
    }

    @Override
    public long countPublished() {
        return entityManager
                .createQuery("SELECT COUNT(h) FROM TypeHierarchyEntry h WHERE h.published = true", Long.class)
                .getSingleResult();
    }
}
