package com.hlag.sourceviewer.domain.port.outgoing;

import com.hlag.sourceviewer.domain.model.identifier.BranchName;
import com.hlag.sourceviewer.domain.model.identifier.CommitSha;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import com.hlag.sourceviewer.domain.model.identifier.*;
import com.hlag.sourceviewer.domain.model.repository.Repository;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

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
     * Returns {@link Optional#empty()} when the file is binary (contains null bytes)
     * and should not be indexed as text.
     */
    Optional<String> readFileContent(Repository repository, FilePath path, CommitSha commitSha);

    /**
     * Lists all file paths present in the repository tree at the given commit.
     */
    List<FilePath> listAllFiles(Repository repository, CommitSha commitSha);

    /**
     * Returns the file paths that were deleted between two commits.
     */
    List<FilePath> deletedFilesBetween(
            Repository repository,
            CommitSha fromCommitSha,
            CommitSha toCommitSha);

    /**
     * Returns {@code true} when the repository has already been cloned to local disk.
     */
    boolean localRepositoryExists(Repository repository);

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

    /**
     * Returns the local filesystem path where the repository is checked out.
     */
    Path getLocalPath(Repository repository);

    /**
     * Deletes the local clone of the repository from disk, if it exists.
     * Does nothing when the repository has never been cloned locally.
     */
    void deleteLocalRepository(Repository repository);
}
