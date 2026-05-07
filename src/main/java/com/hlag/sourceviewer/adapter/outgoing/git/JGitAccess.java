package com.hlag.sourceviewer.adapter.outgoing.git;

import com.hlag.sourceviewer.domain.model.identifier.BranchName;
import com.hlag.sourceviewer.domain.model.identifier.CommitSha;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import com.hlag.sourceviewer.domain.model.repository.Repository;
import com.hlag.sourceviewer.domain.port.outgoing.GitAccess;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@ApplicationScoped
public class JGitAccess implements GitAccess {

    private static final Logger logger = LoggerFactory.getLogger(JGitAccess.class);

    @ConfigProperty(name = "sourceviewer.repos.base-path")
    String reposBasePath;

    @Override
    public CommitSha fetchRemoteHeadSha(Repository repository, BranchName branch) {
        try (org.eclipse.jgit.lib.Repository gitRepository = openGitRepository(repository)) {
            try (Git git = new Git(gitRepository)) {
                git.fetch().setRemote("origin").call();
            } catch (GitAPIException exception) {
                logger.warn("Fetch failed for {}, falling back to local refs: {}",
                        repository.name().value(), exception.getMessage());
            }
            Ref ref = gitRepository.findRef("refs/remotes/origin/" + branch.value());
            if (ref == null) {
                ref = gitRepository.findRef("refs/heads/" + branch.value());
            }
            if (ref == null) {
                throw new IllegalStateException(
                        "Branch '" + branch.value() + "' not found in repository " + repository.name().value());
            }
            return new CommitSha(ref.getObjectId().getName());
        } catch (IOException exception) {
            throw new GitAccessException(
                    "Failed to fetch HEAD SHA for repository " + repository.name().value(), exception);
        }
    }

    @Override
    public List<FilePath> changedFilesBetween(
            Repository repository, CommitSha fromCommitSha, CommitSha toCommitSha) {
        try (org.eclipse.jgit.lib.Repository gitRepository = openGitRepository(repository)) {
            try (Git git = new Git(gitRepository)) {
                AbstractTreeIterator oldTree = prepareTreeParser(gitRepository, fromCommitSha.value());
                AbstractTreeIterator newTree = prepareTreeParser(gitRepository, toCommitSha.value());
                return git.diff()
                        .setOldTree(oldTree)
                        .setNewTree(newTree)
                        .call()
                        .stream()
                        .filter(entry -> entry.getChangeType() != DiffEntry.ChangeType.DELETE)
                        .map(entry -> new FilePath(entry.getNewPath()))
                        .toList();
            }
        } catch (IOException | GitAPIException exception) {
            throw new GitAccessException(
                    "Failed to compute diff for repository " + repository.name().value(), exception);
        }
    }

    @Override
    public String readFileContent(Repository repository, FilePath path, CommitSha commitSha) {
        try (org.eclipse.jgit.lib.Repository gitRepository = openGitRepository(repository)) {
            ObjectId commitId = gitRepository.resolve(commitSha.value());
            try (RevWalk revWalk = new RevWalk(gitRepository)) {
                RevCommit commit = revWalk.parseCommit(commitId);
                RevTree tree = commit.getTree();
                try (TreeWalk treeWalk = TreeWalk.forPath(gitRepository, path.value(), tree)) {
                    if (treeWalk == null) {
                        throw new IllegalArgumentException(
                                "File not found: " + path.value() + " at commit " + commitSha.shortForm());
                    }
                    ObjectId blobId = treeWalk.getObjectId(0);
                    byte[] bytes = gitRepository.open(blobId).getBytes();
                    return new String(bytes, StandardCharsets.UTF_8);
                }
            }
        } catch (IOException exception) {
            throw new GitAccessException(
                    "Failed to read file " + path.value() + " from " + repository.name().value(), exception);
        }
    }

    private org.eclipse.jgit.lib.Repository openGitRepository(Repository repository) throws IOException {
        File repoDir = new File(reposBasePath, repository.name().value());
        return new FileRepositoryBuilder()
                .setGitDir(new File(repoDir, ".git"))
                .readEnvironment()
                .findGitDir()
                .build();
    }

    private AbstractTreeIterator prepareTreeParser(
            org.eclipse.jgit.lib.Repository gitRepository, String commitShaValue) throws IOException {
        ObjectId commitId = gitRepository.resolve(commitShaValue);
        try (RevWalk revWalk = new RevWalk(gitRepository)) {
            RevCommit commit = revWalk.parseCommit(commitId);
            RevTree tree = revWalk.parseTree(commit.getTree().getId());
            CanonicalTreeParser treeParser = new CanonicalTreeParser();
            try (ObjectReader objectReader = gitRepository.newObjectReader()) {
                treeParser.reset(objectReader, tree.getId());
            }
            return treeParser;
        }
    }
}
