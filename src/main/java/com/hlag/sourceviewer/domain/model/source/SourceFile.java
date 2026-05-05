package com.hlag.sourceviewer.domain.model.source;

import com.hlag.sourceviewer.domain.model.identifier.BranchName;
import com.hlag.sourceviewer.domain.model.identifier.DisplayName;
import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import com.hlag.sourceviewer.domain.model.identifier.RepositoryIdentifier;
import com.hlag.sourceviewer.domain.model.repository.ContentSha;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * Metadata of an indexed source file within a repository.
 * The actual content is managed separately via the full-text index.
 */
@Entity
@Table(name = "source_file",
        uniqueConstraints = @UniqueConstraint(columnNames = {"repository_id", "branch", "path"}))
public class SourceFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private FileIdentifier identifier;

    @Column(name = "repository_id", nullable = false)
    private RepositoryIdentifier repositoryIdentifier;

    @Column(name = "branch", nullable = false)
    private BranchName branch;

    @Column(name = "path", nullable = false)
    private FilePath path;

    @Column(name = "content_sha", nullable = false)
    private ContentSha contentSha;

    @Column(name = "language", nullable = false)
    private DisplayName language;

    @Column(name = "indexed_at", nullable = false)
    private Instant indexedAt;

    protected SourceFile() {}

    public SourceFile(
            FileIdentifier identifier,
            RepositoryIdentifier repositoryIdentifier,
            BranchName branch,
            FilePath path,
            ContentSha contentSha,
            DisplayName language,
            Instant indexedAt) {
        this.identifier = identifier;
        this.repositoryIdentifier = repositoryIdentifier;
        this.branch = branch;
        this.path = path;
        this.contentSha = contentSha;
        this.language = language;
        this.indexedAt = indexedAt;
    }

    public FileIdentifier identifier() { return identifier; }
    public RepositoryIdentifier repositoryIdentifier() { return repositoryIdentifier; }
    public BranchName branch() { return branch; }
    public FilePath path() { return path; }
    public ContentSha contentSha() { return contentSha; }
    public DisplayName language() { return language; }
    public Instant indexedAt() { return indexedAt; }

    public void setContentSha(ContentSha contentSha) { this.contentSha = contentSha; }
    public void setIndexedAt(Instant indexedAt) { this.indexedAt = indexedAt; }
}
