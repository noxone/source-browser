package com.hlag.sourceviewer.adapter.incoming.rest.dto;

/**
 * Request body for creating a new Git provider group configuration.
 *
 * @param name             human-readable display name, must be unique
 * @param providerType     the Git hosting provider (e.g. {@code GITLAB}, {@code GITHUB})
 * @param groupPath        the group or organization path within the provider
 * @param baseUrl          optional base URL for self-hosted provider instances; null means use the provider default
 * @param archivedOmitted  {@code true} to exclude archived repositories from indexing
 * @param forkedOmitted    {@code true} to exclude forked repositories from indexing
 * @param sharedOmitted    {@code true} to exclude shared repositories (GitLab only)
 * @param importedOmitted  {@code true} to exclude imported repositories (GitLab only)
 */
public record CreateGitProviderGroupDto(
        String name,
        String providerType,
        String groupPath,
        String baseUrl,
        boolean archivedOmitted,
        boolean forkedOmitted,
        boolean sharedOmitted,
        boolean importedOmitted
) {}
