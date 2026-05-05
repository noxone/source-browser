package com.hlag.sourceviewer.adapter.outgoing.persistence;

import com.hlag.sourceviewer.domain.model.identifier.RepositoryIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.ScanJobIdentifier;
import com.hlag.sourceviewer.domain.model.source.ScanJob;
import com.hlag.sourceviewer.domain.port.outgoing.ScanJobRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PanacheScanJobRepository
        implements ScanJobRepository, PanacheRepositoryBase<ScanJob, ScanJobIdentifier> {

    @Override
    public Optional<ScanJob> findByIdentifier(ScanJobIdentifier identifier) {
        return findByIdOptional(identifier);
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
}
