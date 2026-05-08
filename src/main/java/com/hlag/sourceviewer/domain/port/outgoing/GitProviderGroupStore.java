package com.hlag.sourceviewer.domain.port.outgoing;

import com.hlag.sourceviewer.domain.model.identifier.GitProviderGroupIdentifier;
import com.hlag.sourceviewer.domain.model.repository.GitProviderGroup;

import java.util.List;
import java.util.Optional;

/**
 * Port for accessing persisted Git provider group configurations.
 */
public interface GitProviderGroupStore {

    /**
     * Returns the group configuration with the given identifier, or empty if it does not exist.
     *
     * @param identifier the identifier to look up
     * @return the matching group configuration, or empty
     */
    Optional<GitProviderGroup> findByIdentifier(GitProviderGroupIdentifier identifier);

    /**
     * Returns all configured Git provider groups.
     *
     * @return unordered list of all group configurations
     */
    List<GitProviderGroup> findAll();

    /**
     * Persists a new Git provider group configuration and returns its generated identifier.
     *
     * @param group the group to persist
     * @return the generated identifier assigned to the persisted group
     */
    GitProviderGroupIdentifier insert(GitProviderGroup group);

    /**
     * Updates an existing Git provider group configuration in the store.
     *
     * @param group the group with updated field values
     */
    void update(GitProviderGroup group);

    /**
     * Removes the group configuration with the given identifier from the store.
     * Does nothing if no group with that identifier exists.
     *
     * @param identifier the identifier of the group to remove
     */
    void delete(GitProviderGroupIdentifier identifier);
}
