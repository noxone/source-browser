package com.hlag.sourceviewer.domain.port.incoming;

import com.hlag.sourceviewer.domain.model.identifier.DisplayName;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import com.hlag.sourceviewer.domain.model.identifier.GitProviderGroupIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.GitProviderType;
import com.hlag.sourceviewer.domain.model.identifier.GroupPath;
import com.hlag.sourceviewer.domain.model.repository.GitProviderGroup;
import com.hlag.sourceviewer.domain.model.repository.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Use case for creating, reading, updating and deleting configured Git provider groups.
 */
public interface ManageGitProviderGroupsUseCase {

    /**
     * Returns all configured Git provider groups.
     *
     * @return unordered list of all group configurations
     */
    List<GitProviderGroup> listGitProviderGroups();

    /**
     * Returns the group configuration with the given identifier, or empty if it does not exist.
     *
     * @param identifier the identifier to look up
     * @return the matching group configuration, or empty
     */
    Optional<GitProviderGroup> findGitProviderGroup(GitProviderGroupIdentifier identifier);

    /**
     * Creates a new Git provider group configuration from the given command and persists it.
     *
     * @param command the creation parameters
     * @return the newly created group configuration, including its generated identifier
     */
    GitProviderGroup createGitProviderGroup(CreateGitProviderGroupCommand command);

    /**
     * Updates an existing Git provider group configuration's mutable fields.
     *
     * @param command the update parameters, including the target identifier
     * @return the updated group configuration
     * @throws java.util.NoSuchElementException if no group with the given identifier exists
     */
    GitProviderGroup updateGitProviderGroup(UpdateGitProviderGroupCommand command);

    /**
     * Removes the group configuration with the given identifier.
     * Does nothing if the group does not exist.
     *
     * @param identifier the identifier of the group to remove
     */
    void deleteGitProviderGroup(GitProviderGroupIdentifier identifier);

    /**
     * Returns all repositories that were discovered from the given group.
     *
     * @param identifier the group identifier
     * @return list of repositories belonging to this group
     */
    List<Repository> listGroupRepositories(GitProviderGroupIdentifier identifier);

    /**
     * Returns the number of repositories auto-discovered from the given group.
     *
     * @param identifier the group identifier
     * @return repository count
     */
    long countGroupRepositories(GitProviderGroupIdentifier identifier);

    /**
     * Parameters for creating a new Git provider group configuration.
     *
     * @param name            human-readable display name, must be unique
     * @param providerType    the Git hosting provider
     * @param groupPath       the group or organization path within the provider
     * @param baseUrl         optional base URL for self-hosted provider instances
     * @param archivedOmitted whether archived repositories shall be excluded from indexing
     * @param forkedOmitted   whether forked repositories shall be excluded from indexing
     */
    record CreateGitProviderGroupCommand(
            DisplayName name,
            GitProviderType providerType,
            GroupPath groupPath,
            Optional<FilePath> baseUrl,
            boolean archivedOmitted,
            boolean forkedOmitted
    ) {}

    /**
     * Parameters for updating an existing Git provider group configuration.
     *
     * @param identifier      identifies the group to update
     * @param name            new human-readable display name
     * @param providerType    new Git hosting provider
     * @param groupPath       new group or organization path within the provider
     * @param baseUrl         new base URL, or empty to clear it
     * @param archivedOmitted whether archived repositories shall be excluded from indexing
     * @param forkedOmitted   whether forked repositories shall be excluded from indexing
     */
    record UpdateGitProviderGroupCommand(
            GitProviderGroupIdentifier identifier,
            DisplayName name,
            GitProviderType providerType,
            GroupPath groupPath,
            Optional<FilePath> baseUrl,
            boolean archivedOmitted,
            boolean forkedOmitted
    ) {}
}
