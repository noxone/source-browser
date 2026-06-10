package com.hlag.sourceviewer.domain.port.outgoing;

import com.hlag.sourceviewer.domain.model.identifier.RepositoryIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.ScanJobIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.*;
import com.hlag.sourceviewer.domain.model.source.ScanJob;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Port for accessing scan jobs.
 */
public interface ScanJobRepository {

    Optional<ScanJob> findByIdentifier(ScanJobIdentifier identifier);

    Optional<ScanJob> pollNextQueued();

    List<ScanJob> findAllScanJobs();

    List<ScanJob> findByStatus(ScanJob.ScanJobStatus status);

    List<ScanJob> findByRepository(RepositoryIdentifier repositoryIdentifier);

    ScanJobIdentifier insert(ScanJob scanJob);

    void update(ScanJob scanJob);

    void deleteById(ScanJobIdentifier identifier);

    void deleteAllQueued();

    /** Deletes all scan jobs in {@code DONE} or {@code FAILED} status. */
    void deleteAllFinished();

    /** Returns RUNNING jobs whose heartbeat is older than {@code staleBefore}, or that never sent one. */
    List<ScanJob> findStaleRunningJobs(Instant staleBefore);
}
