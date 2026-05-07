package com.hlag.sourceviewer.adapter.incoming.rest.dto;

/**
 * Request body for creating a new Git repository entry.
 *
 * @param name          display name, must be unique across all repositories
 * @param remoteUrl     optional remote URL for cloning or fetching; null means local-only
 * @param localPath     local filesystem path to the checked-out repository
 * @param defaultBranch name of the branch to index by default; defaults to "main" when null
 */
public record CreateRepositoryDto(
        String name,
        String remoteUrl,
        String localPath,
        String defaultBranch
) {}
