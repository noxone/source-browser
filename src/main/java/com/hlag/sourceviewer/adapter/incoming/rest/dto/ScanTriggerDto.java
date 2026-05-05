package com.hlag.sourceviewer.adapter.incoming.rest.dto;

/**
 * Data transfer object for a manually triggered scan job.
 */
public record ScanTriggerDto(
        Long repositoryId,
        String commitSha
) {}
