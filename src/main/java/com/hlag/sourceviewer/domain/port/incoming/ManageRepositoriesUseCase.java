package com.hlag.sourceviewer.domain.port.incoming;

import com.hlag.sourceviewer.domain.model.identifier.BranchName;
import com.hlag.sourceviewer.domain.model.identifier.DisplayName;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import com.hlag.sourceviewer.domain.model.identifier.RepositoryIdentifier;
import com.hlag.sourceviewer.domain.model.repository.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Use case for creating, reading, updating and deleting configured Git repositories.
 */
public interface ManageRepositoriesUseCase {

    /**
     * Returns all configured repositories.
     *
     * @return unordered list of all repositories
     */
    List<Repository> listRepositories();

    /**
     * Returns the repository with the given identifier, or empty if it does not exist.
     *
     * @param identifier the repository identifier to look up
     * @return the matching repository, or empty
     */
    Optional<Repository> findRepository(RepositoryIdentifier identifier);

    /**
     * Creates a new repository from the given command and persists it.
     *
     * @param command the creation parameters
     * @return the newly created repository, including its generated identifier
     */
    Repository createRepository(CreateRepositoryCommand command);

    /**
     * Updates an existing repository's mutable fields.
     *
     * @param command the update parameters, including the target identifier
     * @return the updated repository
     * @throws java.util.NoSuchElementException if no repository with the given identifier exists
     */
    Repository updateRepository(UpdateRepositoryCommand command);

    /**
     * Removes the repository with the given identifier.
     * Does nothing if the repository does not exist.
     *
     * @param identifier the identifier of the repository to remove
     */
    void deleteRepository(RepositoryIdentifier identifier);

    /**
     * Parameters for creating a new repository.
     *
     * @param name          display name, must be unique
     * @param remoteUrl     optional remote URL for cloning or fetching
     * @param defaultBranch the branch that will be indexed by default
     */
    record CreateRepositoryCommand(
            DisplayName name,
            Optional<FilePath> remoteUrl,
            BranchName defaultBranch
    ) {}

    /**
     * Parameters for updating an existing repository.
     *
     * @param identifier    identifies the repository to update
     * @param name          new display name
     * @param remoteUrl     new remote URL, or empty to clear it
     * @param defaultBranch new default branch
     */
    record UpdateRepositoryCommand(
            RepositoryIdentifier identifier,
            DisplayName name,
            Optional<FilePath> remoteUrl,
            BranchName defaultBranch
    ) {}
}
