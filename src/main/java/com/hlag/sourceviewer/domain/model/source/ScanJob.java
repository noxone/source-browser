package com.hlag.sourceviewer.domain.model.source;

import com.hlag.sourceviewer.domain.model.identifier.CommitSha;
import com.hlag.sourceviewer.domain.model.identifier.ErrorMessage;
import com.hlag.sourceviewer.domain.model.identifier.RepositoryIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.ScanJobIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.TokenCount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Optional;

/** A scan operation for a repository. Triggered by a webhook, a cron job, or manually. */
@Entity
@Table(name = "scan_job")
public class ScanJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private ScanJobIdentifier identifier;

    @Column(name = "repository_id", nullable = false)
    private RepositoryIdentifier repositoryIdentifier;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false)
    private TriggerType triggerType;

    @Column(name = "commit_sha")
    private CommitSha commitSha;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ScanJobStatus status;

    @Column(name = "queued_at", nullable = false)
    private Instant queuedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "files_scanned", nullable = false)
    private TokenCount filesScanned;

    @Column(name = "error_message")
    private ErrorMessage errorMessage;

    protected ScanJob() {}

    public ScanJob(
            ScanJobIdentifier identifier,
            RepositoryIdentifier repositoryIdentifier,
            TriggerType triggerType,
            Optional<CommitSha> commitSha,
            ScanJobStatus status,
            Instant queuedAt,
            Optional<Instant> startedAt,
            Optional<Instant> finishedAt,
            TokenCount filesScanned,
            Optional<ErrorMessage> errorMessage) {
        this.identifier = identifier;
        this.repositoryIdentifier = repositoryIdentifier;
        this.triggerType = triggerType;
        this.commitSha = commitSha.orElse(null);
        this.status = status;
        this.queuedAt = queuedAt;
        this.startedAt = startedAt.orElse(null);
        this.finishedAt = finishedAt.orElse(null);
        this.filesScanned = filesScanned;
        this.errorMessage = errorMessage.orElse(null);
    }

    public ScanJobIdentifier identifier() { return identifier; }
    public RepositoryIdentifier repositoryIdentifier() { return repositoryIdentifier; }
    public TriggerType triggerType() { return triggerType; }
    public Optional<CommitSha> commitSha() { return Optional.ofNullable(commitSha); }
    public ScanJobStatus status() { return status; }
    public Instant queuedAt() { return queuedAt; }
    public Optional<Instant> startedAt() { return Optional.ofNullable(startedAt); }
    public Optional<Instant> finishedAt() { return Optional.ofNullable(finishedAt); }
    public TokenCount filesScanned() { return filesScanned; }
    public Optional<ErrorMessage> errorMessage() { return Optional.ofNullable(errorMessage); }

    public void setStatus(ScanJobStatus status) { this.status = status; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public void setFilesScanned(TokenCount filesScanned) { this.filesScanned = filesScanned; }
    public void setErrorMessage(ErrorMessage errorMessage) { this.errorMessage = errorMessage; }

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
