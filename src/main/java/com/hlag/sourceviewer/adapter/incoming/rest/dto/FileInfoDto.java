package com.hlag.sourceviewer.adapter.incoming.rest.dto;

import java.time.Instant;

public record FileInfoDto(
        Long fileId,
        String filePath,
        String repositoryName,
        String branch,
        String language,
        Instant indexedAt,
        boolean hasTokenStream,
        String lastCommitSha,
        String lastAuthorName,
        String lastAuthorEmail,
        Instant lastCommitDate,
        String lastCommitMessage
) {}
