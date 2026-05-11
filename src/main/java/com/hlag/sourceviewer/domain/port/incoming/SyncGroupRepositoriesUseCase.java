package com.hlag.sourceviewer.domain.port.incoming;

import com.hlag.sourceviewer.domain.model.identifier.GitProviderGroupIdentifier;

/**
 * Use case for synchronising the repository list of a Git provider group with the provider's API.
 */
public interface SyncGroupRepositoriesUseCase {

    /**
     * Fetches the current list of repositories from the provider API for the given group
     * and upserts them into the local store, removing any repos that are no longer present.
     *
     * @param identifier the group to sync
     * @throws java.util.NoSuchElementException if the group does not exist
     * @throws RuntimeException                 if the provider API call fails
     */
    void syncGroup(GitProviderGroupIdentifier identifier);
}
