package com.hlag.sourceviewer.domain.port.incoming;

import com.hlag.sourceviewer.domain.model.identifier.ScanJobIdentifier;
import com.hlag.sourceviewer.domain.model.source.ScanJob;

import java.util.List;
import java.util.Optional;

/**
 * Use case for managing (listing and deleting) scan jobs from the admin UI.
 *
 * <p>Listing supports an optional status filter. Deletion is restricted to jobs
 * in {@code QUEUED} status — running or finished jobs cannot be removed.</p>
 */
public interface ManageScanJobsUseCase {

    /**
     * Returns all scan jobs, optionally filtered by status.
     *
     * @param status when present, only jobs with this status are returned;
     *               when empty, all jobs are returned
     */
    List<ScanJob> listScanJobs(Optional<ScanJob.ScanJobStatus> status);

    /**
     * Deletes a single scan job.
     *
     * @param identifier the ID of the job to delete
     * @throws java.util.NoSuchElementException if the job does not exist
     * @throws IllegalStateException            if the job is not in {@code QUEUED} status
     */
    void deleteScanJob(ScanJobIdentifier identifier);

    /**
     * Deletes all scan jobs that are currently in {@code QUEUED} status.
     */
    void deleteAllQueuedScanJobs();
}
