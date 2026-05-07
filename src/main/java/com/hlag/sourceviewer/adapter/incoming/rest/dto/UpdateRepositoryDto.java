package com.hlag.sourceviewer.adapter.incoming.rest.dto;

/**
 * Request body for updating an existing Git repository entry.
 *
 * @param name          new display name for the repository
 * @param remoteUrl     new remote URL, or null to clear the existing value
 * @param defaultBranch new default branch name
 */
public record UpdateRepositoryDto(
        String name,
        String remoteUrl,
        String defaultBranch
) {}
