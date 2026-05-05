package com.hlag.sourceviewer.domain.port.incoming;

import com.hlag.sourceviewer.domain.model.identifier.RepositoryIdentifier;
import com.hlag.sourceviewer.domain.model.source.ScanJob;
import com.hlag.sourceviewer.domain.model.identifier.CommitSha;

/**
 * Use case: Starts the scan of a repository.
 * Triggered by a webhook or a cron job.
 */
public interface ScanRepositoryUseCase {

    /**
     * Queues a new scan job for the given repository.
     *
     * @param command contains the repository identifier, optional commit SHA, and trigger type
     * @return the created scan job
     */
    ScanJob enqueueScan(ScanCommand command);

    record ScanCommand(
            RepositoryIdentifier repositoryIdentifier,
            java.util.Optional<CommitSha> commitSha,
            ScanJob.TriggerType triggerType
    ) {}
}
