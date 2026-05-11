package com.hlag.sourceviewer.domain.port.outgoing;

import com.hlag.sourceviewer.domain.model.repository.DiscoveredRepository;
import com.hlag.sourceviewer.domain.model.repository.GitProviderGroup;

import java.util.List;

/**
 * Outgoing port for fetching the list of repositories from a remote Git provider group.
 */
public interface GitProviderGroupClient {

    /**
     * Discovers all repositories in the given group that match its filter flags.
     *
     * @param group     the group configuration including filter flags and optional base URL
     * @param apiSecret plaintext API token for authenticating with the provider
     * @return list of discovered repositories
     * @throws RuntimeException if the provider API cannot be reached or returns an error
     */
    List<DiscoveredRepository> discoverRepositories(GitProviderGroup group, String apiSecret);
}
