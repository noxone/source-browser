package com.hlag.sourceviewer.application.lsp;

import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.source.LspHoverResult;
import com.hlag.sourceviewer.domain.port.incoming.GetLspHoverUseCase;
import com.hlag.sourceviewer.domain.port.outgoing.GitAccess;
import com.hlag.sourceviewer.domain.port.outgoing.RepositoryStore;
import com.hlag.sourceviewer.domain.port.outgoing.SourceFileRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Resolves hover and definition information for a given file position
 * by delegating to the appropriate LSP server via {@link LspServerManager}.
 */
@ApplicationScoped
public class LspHoverService implements GetLspHoverUseCase {

    private static final Logger logger = LoggerFactory.getLogger(LspHoverService.class);

    private final SourceFileRepository sourceFileRepository;
    private final RepositoryStore repositoryStore;
    private final GitAccess gitAccess;
    private final LspServerManager lspServerManager;

    @Inject
    public LspHoverService(
            SourceFileRepository sourceFileRepository,
            RepositoryStore repositoryStore,
            GitAccess gitAccess,
            LspServerManager lspServerManager) {
        this.sourceFileRepository = sourceFileRepository;
        this.repositoryStore = repositoryStore;
        this.gitAccess = gitAccess;
        this.lspServerManager = lspServerManager;
    }

    @Override
    public Optional<LspHoverResult> getHover(FileIdentifier fileId, int line, int column) {
        var sourceFile = sourceFileRepository.findByIdentifier(fileId).orElse(null);
        if (sourceFile == null) {
            return Optional.empty();
        }

        var repository = repositoryStore.findByIdentifier(sourceFile.repositoryIdentifier()).orElse(null);
        if (repository == null) {
            return Optional.empty();
        }

        Path workspacePath = gitAccess.getLocalPath(repository);
        String language = sourceFile.language().value().toLowerCase();
        String fileUri = workspacePath.resolve(sourceFile.path().value()).toUri().toString();

        var lspOpt = lspServerManager.getOrCreate(language, workspacePath);
        if (lspOpt.isEmpty()) {
            logger.debug("No LSP server available for language={}", language);
            return Optional.of(LspHoverResult.empty());
        }

        LspServerProcess lsp = lspOpt.get();
        String content = readFileContent(repository, sourceFile, workspacePath);

        // LSP positions are 0-based; domain model uses 1-based
        int lspLine = line - 1;
        int lspColumn = column - 1;

        Optional<String> markdown = lsp.hover(fileUri, content, lspLine, lspColumn);
        Optional<Location> defLocation = lsp.definition(fileUri, content, lspLine, lspColumn);

        Optional<String> defFilePath = defLocation.map(loc -> {
            String defUri = loc.getUri();
            // Convert absolute URI back to repo-relative path if inside the workspace
            String workspaceUri = workspacePath.toUri().toString();
            if (defUri.startsWith(workspaceUri)) {
                return defUri.substring(workspaceUri.length()).replaceAll("^/+", "");
            }
            return defUri;
        });

        Optional<Integer> defLine = defLocation.map(loc -> toOneBased(loc.getRange(), true));
        Optional<Integer> defColumn = defLocation.map(loc -> toOneBased(loc.getRange(), false));

        return Optional.of(new LspHoverResult(markdown, defFilePath, defLine, defColumn));
    }

    private String readFileContent(
            com.hlag.sourceviewer.domain.model.repository.Repository repository,
            com.hlag.sourceviewer.domain.model.source.SourceFile sourceFile,
            Path workspacePath) {
        // Try reading from disk (fastest path when repo is cloned)
        Path absolutePath = workspacePath.resolve(sourceFile.path().value());
        try {
            return java.nio.file.Files.readString(absolutePath);
        } catch (Exception e) {
            logger.debug("Could not read {} from disk, falling back to git: {}", absolutePath, e.getMessage());
        }
        // Fallback: read from git object store at HEAD commit
        return repository.lastCommitSha()
                .flatMap(sha -> gitAccess.readFileContent(repository, sourceFile.path(), sha))
                .orElse("");
    }

    private static int toOneBased(Range range, boolean line) {
        if (range == null || range.getStart() == null) return 1;
        return (line ? range.getStart().getLine() : range.getStart().getCharacter()) + 1;
    }
}
