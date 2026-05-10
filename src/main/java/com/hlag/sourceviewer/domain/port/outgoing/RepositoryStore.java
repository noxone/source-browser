package com.hlag.sourceviewer.domain.port.outgoing;

import com.hlag.sourceviewer.domain.model.identifier.DisplayName;
import com.hlag.sourceviewer.domain.model.identifier.GitProviderGroupIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.RepositoryIdentifier;
import com.hlag.sourceviewer.domain.model.repository.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Port for accessing persisted repository entries.
 * Named RepositoryStore (not RepositoryRepository) to avoid naming conflicts
 * with the domain concept "Repository".
 */
public interface RepositoryStore {

    Optional<Repository> findByIdentifier(RepositoryIdentifier identifier);

    Optional<Repository> findByName(DisplayName name);

    /** Returns all repositories regardless of origin (manual or group-discovered). */
    List<Repository> findAll();

    /** Returns only manually managed repositories (not auto-discovered from a group). */
    List<Repository> findAllManual();

    /** Returns all repositories auto-discovered from the given group. */
    List<Repository> findByGroup(GitProviderGroupIdentifier groupIdentifier);

    /** Returns the number of repositories auto-discovered from the given group. */
    long countByGroup(GitProviderGroupIdentifier groupIdentifier);

    RepositoryIdentifier insert(Repository repository);

    void update(Repository repository);

    /**
     * Removes the repository with the given identifier from the store.
     * Does nothing if no repository with that identifier exists.
     *
     * @param identifier the identifier of the repository to remove
     */
    void delete(RepositoryIdentifier identifier);

    /**
     * Deletes all group-discovered repositories whose remote URL is NOT in the given set.
     * Used during group sync to remove repos that have disappeared from the provider.
     *
     * @param groupIdentifier the group whose stale repos should be removed
     * @param activeRemoteUrls remote URLs that are still active; repos with other URLs are deleted
     */
    void deleteStaleGroupRepositories(GitProviderGroupIdentifier groupIdentifier, java.util.Set<String> activeRemoteUrls);
}
