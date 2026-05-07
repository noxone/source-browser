package com.hlag.sourceviewer.adapter.incoming.rest.dto;

/**
 * Data transfer object representing a configured Git repository.
 *
 * @param id            unique numeric identifier
 * @param name          display name of the repository
 * @param remoteUrl     optional remote URL for cloning or fetching, may be null
 * @param localPath     local filesystem path to the checked-out repository
 * @param defaultBranch name of the branch that is indexed by default
 * @param lastScannedAt ISO-8601 timestamp of the last completed scan, may be null
 * @param lastCommitSha SHA of the last scanned commit, may be null
 */
public record RepositoryDto(
        Long id,
        String name,
        String remoteUrl,
        String localPath,
        String defaultBranch,
        String lastScannedAt,
        String lastCommitSha
) {}
