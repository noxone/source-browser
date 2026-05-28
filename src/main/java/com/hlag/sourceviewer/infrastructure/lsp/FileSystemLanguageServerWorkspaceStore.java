package com.hlag.sourceviewer.infrastructure.lsp;

import com.hlag.sourceviewer.domain.model.repository.Repository;
import com.hlag.sourceviewer.domain.port.incoming.ManageAppSettingsUseCase;
import com.hlag.sourceviewer.domain.port.outgoing.LanguageServerWorkspaceStore;
import com.hlag.sourceviewer.domain.service.RepositoryStorageDirectoryNameResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Filesystem-backed store for persistent language-server workspace directories. */
@ApplicationScoped
public class FileSystemLanguageServerWorkspaceStore implements LanguageServerWorkspaceStore {

    private static final Logger logger = LoggerFactory.getLogger(FileSystemLanguageServerWorkspaceStore.class);

    private final ManageAppSettingsUseCase manageAppSettingsUseCase;

    @Inject
    public FileSystemLanguageServerWorkspaceStore(ManageAppSettingsUseCase manageAppSettingsUseCase) {
        this.manageAppSettingsUseCase = manageAppSettingsUseCase;
    }

    @Override
    public Path resolveWorkspacePath(Repository repository) {
        Path base = resolveBasePath();
        Path workspace = base.resolve(RepositoryStorageDirectoryNameResolver.localDirectoryName(repository));
        try {
            Files.createDirectories(workspace);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create language-server workspace directory: " + workspace, exception);
        }
        return workspace;
    }

    @Override
    public void deleteWorkspace(Repository repository) {
        Path workspace = resolveBasePath().resolve(RepositoryStorageDirectoryNameResolver.localDirectoryName(repository));
        if (!Files.exists(workspace)) {
            return;
        }
        try (var walk = Files.walk(workspace)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException exception) {
                    logger.warn("Could not delete LSP workspace path {}: {}", path, exception.getMessage());
                }
            });
        } catch (IOException exception) {
            logger.warn("Could not delete LSP workspace {}: {}", workspace, exception.getMessage());
        }
    }

    private Path resolveBasePath() {
        String configured = manageAppSettingsUseCase.getSetting(
                ManageAppSettingsUseCase.SETTING_LSP_WORKSPACE_BASE_PATH,
                ManageAppSettingsUseCase.DEFAULT_LSP_WORKSPACE_BASE_PATH);
        if (configured == null || configured.isBlank()) {
            return Path.of(System.getProperty("user.home"), "sourceviewer-lsp-workspaces");
        }
        return Path.of(configured);
    }
}

