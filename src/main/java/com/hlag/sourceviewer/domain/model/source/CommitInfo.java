package com.hlag.sourceviewer.domain.model.source;

import java.time.Instant;

/**
 * Metadata about a single Git commit, used to display last-change information
 * for a source file in the file viewer.
 */
public record CommitInfo(
        String sha,
        String authorName,
        String authorEmail,
        Instant commitDate,
        String message
) {}
