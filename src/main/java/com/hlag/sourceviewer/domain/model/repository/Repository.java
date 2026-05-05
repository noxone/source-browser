package com.hlag.sourceviewer.domain.model.repository;

import com.hlag.sourceviewer.domain.model.identifier.BranchName;
import com.hlag.sourceviewer.domain.model.identifier.CommitSha;
import com.hlag.sourceviewer.domain.model.identifier.DisplayName;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import com.hlag.sourceviewer.domain.model.identifier.RepositoryIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.*;

import java.time.Instant;
import java.util.Optional;

/**
 * Ein Git-Repository das vom Source Viewer indiziert wird.
 */
public record Repository(
        RepositoryIdentifier identifier,
        DisplayName name,
        Optional<FilePath> remoteUrl,
        FilePath localPath,
        BranchName defaultBranch,
        Optional<Instant> lastScannedAt,
        Optional<CommitSha> lastCommitSha
) {}
