package com.hlag.sourceviewer.domain.port.outgoing;

import com.hlag.sourceviewer.domain.model.identifier.BranchName;
import com.hlag.sourceviewer.domain.model.identifier.CommitSha;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import com.hlag.sourceviewer.domain.model.identifier.*;
import com.hlag.sourceviewer.domain.model.repository.Repository;

import java.util.List;

/**
 * Port for accessing Git repositories.
 * Implemented by {@code com.hlag.sourceviewer.adapter.outgoing.git.JGitAccess}.
 */
public interface GitAccess {

    /**
     * Returns the current HEAD commit SHA of the remote repository.
     */
    CommitSha fetchRemoteHeadSha(Repository repository, BranchName branch);

    /**
     * Returns the list of file paths that changed between two commits
     * (added, modified, renamed).
     */
    List<FilePath> changedFilesBetween(
            Repository repository,
            CommitSha fromCommitSha,
            CommitSha toCommitSha);

    /**
     * Reads the content of a file at a specific commit.
     */
    String readFileContent(Repository repository, FilePath path, CommitSha commitSha);
}
