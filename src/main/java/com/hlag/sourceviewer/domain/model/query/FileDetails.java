package com.hlag.sourceviewer.domain.model.query;

import java.time.Instant;

public record FileDetails(
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
