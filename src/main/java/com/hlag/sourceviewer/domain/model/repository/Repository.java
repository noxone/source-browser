package com.hlag.sourceviewer.domain.model.repository;

import com.hlag.sourceviewer.domain.model.identifier.BranchName;
import com.hlag.sourceviewer.domain.model.identifier.CommitSha;
import com.hlag.sourceviewer.domain.model.identifier.DisplayName;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import com.hlag.sourceviewer.domain.model.identifier.GitProviderGroupIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.RepositoryIdentifier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Optional;

/** A Git repository indexed by the source viewer. */
@Entity
@Table(name = "repository")
public class Repository {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private DisplayName name;

    @Column(name = "remote_url")
    private FilePath remoteUrl;

    @Column(name = "default_branch", nullable = false)
    private BranchName defaultBranch;

    @Column(name = "last_scanned_at")
    private Instant lastScannedAt;

    @Column(name = "last_commit_sha")
    private CommitSha lastCommitSha;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_group_id")
    private GitProviderGroup sourceGroup;

    protected Repository() {}

    public Repository(
            DisplayName name,
            Optional<FilePath> remoteUrl,
            BranchName defaultBranch,
            Optional<Instant> lastScannedAt,
            Optional<CommitSha> lastCommitSha,
            Optional<GitProviderGroup> sourceGroup) {
        this.name = name;
        this.remoteUrl = remoteUrl.orElse(null);
        this.defaultBranch = defaultBranch;
        this.lastScannedAt = lastScannedAt.orElse(null);
        this.lastCommitSha = lastCommitSha.orElse(null);
        this.sourceGroup = sourceGroup.orElse(null);
    }

    public RepositoryIdentifier identifier() { return id != null ? new RepositoryIdentifier(id) : null; }
    public DisplayName name() { return name; }
    public Optional<FilePath> remoteUrl() { return Optional.ofNullable(remoteUrl); }
    public BranchName defaultBranch() { return defaultBranch; }
    public Optional<Instant> lastScannedAt() { return Optional.ofNullable(lastScannedAt); }
    public Optional<CommitSha> lastCommitSha() { return Optional.ofNullable(lastCommitSha); }
    public Optional<GitProviderGroup> sourceGroup() { return Optional.ofNullable(sourceGroup); }
    public Optional<GitProviderGroupIdentifier> sourceGroupIdentifier() {
        return sourceGroup != null ? Optional.ofNullable(sourceGroup.identifier()) : Optional.empty();
    }

    public boolean isManagedByGroup() { return sourceGroup != null; }

    public void setName(DisplayName name) { this.name = name; }
    public void setRemoteUrl(FilePath remoteUrl) { this.remoteUrl = remoteUrl; }
    public void setDefaultBranch(BranchName defaultBranch) { this.defaultBranch = defaultBranch; }
    public void setLastScannedAt(Instant lastScannedAt) { this.lastScannedAt = lastScannedAt; }
    public void setLastCommitSha(CommitSha lastCommitSha) { this.lastCommitSha = lastCommitSha; }
}
