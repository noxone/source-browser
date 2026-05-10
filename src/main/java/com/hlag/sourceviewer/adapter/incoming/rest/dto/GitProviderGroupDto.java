package com.hlag.sourceviewer.adapter.incoming.rest.dto;

/**
 * Data transfer object representing a configured Git provider group.
 *
 * @param id              unique numeric identifier
 * @param name            human-readable display name of the group configuration
 * @param providerType    the Git hosting provider (e.g. {@code GITLAB}, {@code GITHUB})
 * @param groupPath       the group or organization path within the provider
 * @param baseUrl         optional base URL for self-hosted provider instances, may be null
 * @param archivedOmitted {@code true} if archived repositories are excluded from indexing
 * @param forkedOmitted   {@code true} if forked repositories are excluded from indexing
 * @param repositoryCount number of repositories currently discovered from this group
 */
public record GitProviderGroupDto(
        Long id,
        String name,
        String providerType,
        String groupPath,
        String baseUrl,
        boolean archivedOmitted,
        boolean forkedOmitted,
        long repositoryCount
) {}
