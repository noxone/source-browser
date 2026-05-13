package com.hlag.sourceviewer.adapter.outgoing.git;

import com.hlag.sourceviewer.domain.model.identifier.BranchName;
import com.hlag.sourceviewer.domain.model.identifier.CommitSha;
import com.hlag.sourceviewer.domain.model.identifier.CredentialScopeIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.CredentialScopeType;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import com.hlag.sourceviewer.domain.model.repository.Repository;
import com.hlag.sourceviewer.domain.port.outgoing.GitAccess;
import com.hlag.sourceviewer.domain.port.outgoing.GitCredentialStore;
import com.hlag.sourceviewer.domain.port.outgoing.SecretEncryptor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class JGitAccess implements GitAccess {

    private static final Logger logger = LoggerFactory.getLogger(JGitAccess.class);

    @ConfigProperty(name = "sourceviewer.repos.base-path")
    Optional<String> reposBasePath;

    @Inject
    GitCredentialStore gitCredentialStore;

    @Inject
    SecretEncryptor secretEncryptor;

    @Override
    public boolean localRepositoryExists(Repository repository) {
        return resolveRepoDir(repository).resolve(".git").toFile().isDirectory();
    }

    @Override
    public void prepareRepository(Repository repository) {
        String remoteUrl = repository.remoteUrl()
                .map(FilePath::value)
                .orElseThrow(() -> new IllegalStateException(
                        "Repository '" + repository.name().value() + "' has no remote URL configured"));

        String defaultBranch = repository.defaultBranch().value();
        Path repoDir = resolveRepoDir(repository);
        Optional<UsernamePasswordCredentialsProvider> credentials = credentialsForRepository(repository);

        try {
            if (repoDir.resolve(".git").toFile().isDirectory()) {
                logger.info("Repository '{}' already cloned at {}, updating to origin/{}",
                        repository.name().value(), repoDir, defaultBranch);
                try (org.eclipse.jgit.lib.Repository gitRepository = openGitRepository(repository)) {
                    try (Git git = new Git(gitRepository)) {
                        git.checkout().setName(defaultBranch).call();
                        var fetchCommand = git.fetch().setRemote("origin");
                        credentials.ifPresent(fetchCommand::setCredentialsProvider);
                        fetchCommand.call();
                        git.reset()
                                .setMode(ResetCommand.ResetType.HARD)
                                .setRef("origin/" + defaultBranch)
                                .call();
                    }
                }
                logger.info("Repository '{}' updated successfully", repository.name().value());
            } else {
                logger.info("Cloning repository '{}' from {} into {}",
                        repository.name().value(), remoteUrl, repoDir);
                var cloneCommand = Git.cloneRepository()
                        .setURI(remoteUrl)
                        .setDirectory(repoDir.toFile())
                        .setBranch(defaultBranch);
                credentials.ifPresent(cloneCommand::setCredentialsProvider);
                try (Git ignored = cloneCommand.call()) {
                    // closed immediately; subsequent access uses openGitRepository
                }
                logger.info("Repository '{}' cloned successfully", repository.name().value());
            }
        } catch (IOException | GitAPIException exception) {
            throw new GitAccessException(
                    "Failed to prepare repository '" + repository.name().value() + "'", exception);
        }
    }

    @Override
    public CommitSha fetchRemoteHeadSha(Repository repository, BranchName branch) {
        try (org.eclipse.jgit.lib.Repository gitRepository = openGitRepository(repository)) {
            try (Git git = new Git(gitRepository)) {
                var fetchCommand = git.fetch().setRemote("origin");
                credentialsForRepository(repository).ifPresent(fetchCommand::setCredentialsProvider);
                fetchCommand.call();
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
    public List<FilePath> listAllFiles(Repository repository, CommitSha commitSha) {
        try (org.eclipse.jgit.lib.Repository gitRepository = openGitRepository(repository)) {
            ObjectId commitId = gitRepository.resolve(commitSha.value());
            try (RevWalk revWalk = new RevWalk(gitRepository)) {
                RevCommit commit = revWalk.parseCommit(commitId);
                try (TreeWalk treeWalk = new TreeWalk(gitRepository)) {
                    treeWalk.addTree(commit.getTree());
                    treeWalk.setRecursive(true);
                    List<FilePath> paths = new java.util.ArrayList<>();
                    while (treeWalk.next()) {
                        paths.add(new FilePath(treeWalk.getPathString()));
                    }
                    return paths;
                }
            }
        } catch (IOException exception) {
            throw new GitAccessException(
                    "Failed to list all files in repository " + repository.name().value(), exception);
        }
    }

    @Override
    public List<FilePath> deletedFilesBetween(
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
                        .filter(entry -> entry.getChangeType() == DiffEntry.ChangeType.DELETE)
                        .map(entry -> new FilePath(entry.getOldPath()))
                        .toList();
            }
        } catch (IOException | GitAPIException exception) {
            throw new GitAccessException(
                    "Failed to compute deleted files for repository " + repository.name().value(), exception);
        }
    }

    @Override
    public Optional<String> readFileContent(Repository repository, FilePath path, CommitSha commitSha) {
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
                    if (containsNullByte(bytes)) {
                        return Optional.empty();
                    }
                    return Optional.of(new String(bytes, StandardCharsets.UTF_8));
                }
            }
        } catch (IOException exception) {
            throw new GitAccessException(
                    "Failed to read file " + path.value() + " from " + repository.name().value(), exception);
        }
    }

    private static boolean containsNullByte(byte[] bytes) {
        for (byte b : bytes) {
            if (b == 0) return true;
        }
        return false;
    }

    private org.eclipse.jgit.lib.Repository openGitRepository(Repository repository) throws IOException {
        File repoDir = resolveRepoDir(repository).toFile();
        return new FileRepositoryBuilder()
                .setGitDir(new File(repoDir, ".git"))
                .readEnvironment()
                .findGitDir()
                .build();
    }

    @Override
    public Path getLocalPath(Repository repository) {
        return resolveRepoDir(repository);
    }

    @Override
    public void deleteLocalRepository(Repository repository) {
        Path repoDir = resolveRepoDir(repository);
        if (!Files.exists(repoDir)) {
            return;
        }
        try (var paths = Files.walk(repoDir)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            logger.warn("Could not delete {}: {}", p, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            logger.warn("Failed to delete local repository directory {}: {}", repoDir, e.getMessage());
            return;
        }
        logger.info("Deleted local repository directory {}", repoDir);
    }

    private Path resolveRepoDir(Repository repository) {
        return resolveReposDir().resolve(localDirName(repository));
    }

    private Path resolveReposDir() {
        return reposBasePath
                .filter(s -> !s.isBlank())
                .map(Path::of)
                .orElse(Path.of(System.getProperty("java.io.tmpdir"), "sourceviewer-repos"));
    }

    private String localDirName(Repository repository) {
        String id = "repo-" + repository.identifier().value();
        return repository.remoteUrl()
                .map(url -> id + "-" + sanitizedRemotePath(url.value()))
                .orElse(id);
    }

    private String sanitizedRemotePath(String remoteUrl) {
        try {
            String path = URI.create(remoteUrl).getPath();
            if (path.endsWith(".git")) {
                path = path.substring(0, path.length() - 4);
            }
            // Replace every non-alphanumeric character with '-', collapse runs, trim edges
            return path.replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-|-$", "");
        } catch (Exception exception) {
            // Fall back to a safe hash-free name if the URL is not parseable as a URI
            return remoteUrl.replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-|-$", "");
        }
    }

    private Optional<UsernamePasswordCredentialsProvider> credentialsForRepository(Repository repository) {
        if (repository.isManagedByGroup()) {
            return repository.sourceGroupIdentifier().flatMap(groupId -> {
                // Try dedicated clone credential first, fall back to the API credential
                var credential = gitCredentialStore
                        .findByScope(CredentialScopeType.GROUP_CLONE, new CredentialScopeIdentifier(groupId.value()))
                        .or(() -> gitCredentialStore
                                .findByScope(CredentialScopeType.GROUP, new CredentialScopeIdentifier(groupId.value())));
                return credential.map(c -> {
                    String secret = secretEncryptor.decrypt(c.encryptedSecret()).value();
                    return new UsernamePasswordCredentialsProvider("oauth2", secret);
                });
            });
        }
        if (repository.identifier() == null) {
            return Optional.empty();
        }
        return gitCredentialStore
                .findByScope(
                        CredentialScopeType.REPOSITORY,
                        new CredentialScopeIdentifier(repository.identifier().value()))
                .map(credential -> {
                    String secret = secretEncryptor.decrypt(credential.encryptedSecret()).value();
                    return new UsernamePasswordCredentialsProvider("oauth2", secret);
                });
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
