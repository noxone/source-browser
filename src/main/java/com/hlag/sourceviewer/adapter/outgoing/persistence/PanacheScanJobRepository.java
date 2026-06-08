package com.hlag.sourceviewer.adapter.outgoing.persistence;

import com.hlag.sourceviewer.domain.model.identifier.RepositoryIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.ScanJobIdentifier;
import com.hlag.sourceviewer.domain.model.source.ScanJob;
import com.hlag.sourceviewer.domain.port.outgoing.ScanJobRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PanacheScanJobRepository
        implements ScanJobRepository, PanacheRepositoryBase<ScanJob, Long> {

    @Override
    public Optional<ScanJob> findByIdentifier(ScanJobIdentifier identifier) {
        return findByIdOptional(identifier.value());
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public Optional<ScanJob> pollNextQueued() {
        List<ScanJob> results = getEntityManager()
                .createNativeQuery(
                        "SELECT * FROM scan_job WHERE status = 'QUEUED' " +
                        "ORDER BY queued_at FOR UPDATE SKIP LOCKED LIMIT 1",
                        ScanJob.class)
                .getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public List<ScanJob> findAllScanJobs() {
        return listAll();
    }

    @Override
    public List<ScanJob> findByStatus(ScanJob.ScanJobStatus status) {
        return list("status", status);
    }

    @Override
    public List<ScanJob> findByRepository(RepositoryIdentifier repositoryIdentifier) {
        return list("repositoryIdentifier", repositoryIdentifier);
    }

    @Override
    @Transactional
    public ScanJobIdentifier insert(ScanJob scanJob) {
        persist(scanJob);
        return scanJob.identifier();
    }

    @Override
    @Transactional
    public void update(ScanJob scanJob) {
        getEntityManager().merge(scanJob);
    }

    @Override
    @Transactional
    public void deleteById(ScanJobIdentifier identifier) {
        deleteById(identifier.value());
    }

    @Override
    @Transactional
    public void deleteAllQueued() {
        delete("status", ScanJob.ScanJobStatus.QUEUED);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ScanJob> findStaleRunningJobs(Instant staleBefore) {
        return getEntityManager()
                .createNativeQuery(
                        "SELECT * FROM scan_job WHERE status = 'RUNNING' " +
                        "AND (last_heartbeat_at IS NULL OR last_heartbeat_at < :threshold)",
                        ScanJob.class)
                .setParameter("threshold", staleBefore)
                .getResultList();
    }
}
