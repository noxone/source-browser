package com.hlag.sourceviewer.domain.model.source;

import com.hlag.sourceviewer.domain.model.identifier.BranchName;
import com.hlag.sourceviewer.domain.model.identifier.DisplayName;
import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import com.hlag.sourceviewer.domain.model.identifier.RepositoryIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.*;
import com.hlag.sourceviewer.domain.model.repository.ContentSha;

import java.time.Instant;

/**
 * Metadata of an indexed source file within a repository.
 * The actual content is managed separately via the full-text index.
 */
public record SourceFile(
        FileIdentifier identifier,
        RepositoryIdentifier repositoryIdentifier,
        BranchName branch,
        FilePath path,
        ContentSha contentSha,
        DisplayName language,
        Instant indexedAt
) {}
