package com.hlag.sourceviewer.domain.model.repository;

import com.hlag.sourceviewer.domain.model.converter.BranchNameConverter;
import com.hlag.sourceviewer.domain.model.converter.CommitShaConverter;
import com.hlag.sourceviewer.domain.model.converter.DisplayNameConverter;
import com.hlag.sourceviewer.domain.model.converter.FilePathConverter;
import com.hlag.sourceviewer.domain.model.converter.RepositoryIdentifierConverter;
import com.hlag.sourceviewer.domain.model.identifier.BranchName;
import com.hlag.sourceviewer.domain.model.identifier.CommitSha;
import com.hlag.sourceviewer.domain.model.identifier.DisplayName;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import com.hlag.sourceviewer.domain.model.identifier.RepositoryIdentifier;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Optional;

/** Ein Git-Repository das vom Source Viewer indiziert wird. */
@Entity
@Table(name = "repository")
public class Repository {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Convert(converter = RepositoryIdentifierConverter.class)
    private RepositoryIdentifier identifier;

    @Column(name = "name", nullable = false, unique = true)
    @Convert(converter = DisplayNameConverter.class)
    private DisplayName name;

    @Column(name = "remote_url")
    @Convert(converter = FilePathConverter.class)
    private FilePath remoteUrl;

    @Column(name = "local_path", nullable = false)
    @Convert(converter = FilePathConverter.class)
    private FilePath localPath;

    @Column(name = "default_branch", nullable = false)
    @Convert(converter = BranchNameConverter.class)
    private BranchName defaultBranch;

    @Column(name = "last_scanned_at")
    private Instant lastScannedAt;

    @Column(name = "last_commit_sha")
    @Convert(converter = CommitShaConverter.class)
    private CommitSha lastCommitSha;

    protected Repository() {}

    public Repository(
            RepositoryIdentifier identifier,
            DisplayName name,
            Optional<FilePath> remoteUrl,
            FilePath localPath,
            BranchName defaultBranch,
            Optional<Instant> lastScannedAt,
            Optional<CommitSha> lastCommitSha) {
        this.identifier = identifier;
        this.name = name;
        this.remoteUrl = remoteUrl.orElse(null);
        this.localPath = localPath;
        this.defaultBranch = defaultBranch;
        this.lastScannedAt = lastScannedAt.orElse(null);
        this.lastCommitSha = lastCommitSha.orElse(null);
    }

    public RepositoryIdentifier identifier() { return identifier; }
    public DisplayName name() { return name; }
    public Optional<FilePath> remoteUrl() { return Optional.ofNullable(remoteUrl); }
    public FilePath localPath() { return localPath; }
    public BranchName defaultBranch() { return defaultBranch; }
    public Optional<Instant> lastScannedAt() { return Optional.ofNullable(lastScannedAt); }
    public Optional<CommitSha> lastCommitSha() { return Optional.ofNullable(lastCommitSha); }

    public void setName(DisplayName name) { this.name = name; }
    public void setRemoteUrl(FilePath remoteUrl) { this.remoteUrl = remoteUrl; }
    public void setLocalPath(FilePath localPath) { this.localPath = localPath; }
    public void setDefaultBranch(BranchName defaultBranch) { this.defaultBranch = defaultBranch; }
    public void setLastScannedAt(Instant lastScannedAt) { this.lastScannedAt = lastScannedAt; }
    public void setLastCommitSha(CommitSha lastCommitSha) { this.lastCommitSha = lastCommitSha; }
}
