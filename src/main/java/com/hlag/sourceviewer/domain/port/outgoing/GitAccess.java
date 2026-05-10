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

    /**
     * Ensures the repository is cloned locally and on the default branch, ready
     * for file access operations.
     *
     * <p>If the repository has not been cloned yet, it is cloned from
     * {@link Repository#remoteUrl()}. If it is already present on disk, the
     * default branch is checked out and the latest changes are fetched from
     * {@code origin}.</p>
     *
     * @throws IllegalStateException if no remote URL is configured
     */
    void prepareRepository(Repository repository);
}
