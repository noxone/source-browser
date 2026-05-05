package com.hlag.sourceviewer.domain.model.source;

import com.hlag.sourceviewer.domain.model.identifier.CommitSha;
import com.hlag.sourceviewer.domain.model.identifier.ErrorMessage;
import com.hlag.sourceviewer.domain.model.identifier.RepositoryIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.ScanJobIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.TokenCount;
import com.hlag.sourceviewer.domain.model.identifier.*;

import java.time.Instant;
import java.util.Optional;

/**
 * A scan operation for a repository.
 * Triggered by a webhook, a cron job, or manually.
 */
public record ScanJob(
        ScanJobIdentifier identifier,
        RepositoryIdentifier repositoryIdentifier,
        TriggerType triggerType,
        Optional<CommitSha> commitSha,
        ScanJobStatus status,
        Instant queuedAt,
        Optional<Instant> startedAt,
        Optional<Instant> finishedAt,
        TokenCount filesScanned,
        Optional<ErrorMessage> errorMessage
) {

    public enum TriggerType {
        WEBHOOK,
        CRON,
        MANUAL
    }

    public enum ScanJobStatus {
        QUEUED,
        RUNNING,
        DONE,
        FAILED
    }
}
