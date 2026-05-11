package com.hlag.sourceviewer.adapter.incoming.rest.dto;

/**
 * Request body for updating an existing Git provider group configuration.
 *
 * @param name             new human-readable display name
 * @param providerType     new Git hosting provider (e.g. {@code GITLAB}, {@code GITHUB})
 * @param groupPath        new group or organization path within the provider
 * @param baseUrl          new base URL for self-hosted provider instances; null to clear
 * @param archivedOmitted  {@code true} to exclude archived repositories from indexing
 * @param forkedOmitted    {@code true} to exclude forked repositories from indexing
 * @param sharedOmitted    {@code true} to exclude shared repositories (GitLab only)
 * @param importedOmitted  {@code true} to exclude imported repositories (GitLab only)
 */
public record UpdateGitProviderGroupDto(
        String name,
        String providerType,
        String groupPath,
        String baseUrl,
        boolean archivedOmitted,
        boolean forkedOmitted,
        boolean sharedOmitted,
        boolean importedOmitted
) {}
