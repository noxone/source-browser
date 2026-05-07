package com.hlag.sourceviewer.domain.model.repository;

import com.hlag.sourceviewer.domain.model.identifier.DisplayName;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import com.hlag.sourceviewer.domain.model.identifier.GitProviderGroupIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.GitProviderType;
import com.hlag.sourceviewer.domain.model.identifier.GroupPath;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Optional;

/**
 * A Git provider group (e.g. a GitLab group or a GitHub organization) whose repositories
 * shall be discovered and indexed by the source viewer.
 *
 * <p>The entity stores configuration only — the actual repository discovery
 * is performed separately.</p>
 */
@Entity
@Table(name = "git_provider_group")
public class GitProviderGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private DisplayName name;

    @Column(name = "provider_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private GitProviderType providerType;

    @Column(name = "group_path", nullable = false)
    private GroupPath groupPath;

    @Column(name = "base_url")
    private FilePath baseUrl;

    @Column(name = "is_archived_omitted", nullable = false)
    private boolean archivedOmitted;

    @Column(name = "is_forked_omitted", nullable = false)
    private boolean forkedOmitted;

    protected GitProviderGroup() {}

    /**
     * Creates a new Git provider group configuration.
     *
     * @param name            human-readable display name, must be unique
     * @param providerType    the Git hosting provider
     * @param groupPath       the group or organization path within the provider
     * @param baseUrl         optional base URL for self-hosted provider instances
     * @param archivedOmitted {@code true} if archived repositories shall be excluded from indexing
     * @param forkedOmitted   {@code true} if forked repositories shall be excluded from indexing
     */
    public GitProviderGroup(
            DisplayName name,
            GitProviderType providerType,
            GroupPath groupPath,
            Optional<FilePath> baseUrl,
            boolean archivedOmitted,
            boolean forkedOmitted) {
        this.name = name;
        this.providerType = providerType;
        this.groupPath = groupPath;
        this.baseUrl = baseUrl.orElse(null);
        this.archivedOmitted = archivedOmitted;
        this.forkedOmitted = forkedOmitted;
    }

    /** Returns the unique identifier of this group configuration, or {@code null} before first persist. */
    public GitProviderGroupIdentifier identifier() {
        return id != null ? new GitProviderGroupIdentifier(id) : null;
    }

    /** Returns the human-readable display name. */
    public DisplayName name() { return name; }

    /** Returns the Git hosting provider type. */
    public GitProviderType providerType() { return providerType; }

    /** Returns the group or organization path within the provider. */
    public GroupPath groupPath() { return groupPath; }

    /** Returns the optional base URL for self-hosted provider instances. */
    public Optional<FilePath> baseUrl() { return Optional.ofNullable(baseUrl); }

    /** Returns {@code true} if archived repositories shall be excluded from indexing. */
    public boolean isArchivedOmitted() { return archivedOmitted; }

    /** Returns {@code true} if forked repositories shall be excluded from indexing. */
    public boolean isForkedOmitted() { return forkedOmitted; }

    /** Sets the human-readable display name. */
    public void setName(DisplayName name) { this.name = name; }

    /** Sets the Git hosting provider type. */
    public void setProviderType(GitProviderType providerType) { this.providerType = providerType; }

    /** Sets the group or organization path within the provider. */
    public void setGroupPath(GroupPath groupPath) { this.groupPath = groupPath; }

    /** Sets the base URL for self-hosted provider instances, or {@code null} to clear it. */
    public void setBaseUrl(FilePath baseUrl) { this.baseUrl = baseUrl; }

    /** Sets whether archived repositories shall be excluded from indexing. */
    public void setArchivedOmitted(boolean archivedOmitted) { this.archivedOmitted = archivedOmitted; }

    /** Sets whether forked repositories shall be excluded from indexing. */
    public void setForkedOmitted(boolean forkedOmitted) { this.forkedOmitted = forkedOmitted; }
}
