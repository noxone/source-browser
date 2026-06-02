package com.hlag.sourceviewer.application.storage;

import com.hlag.sourceviewer.domain.port.incoming.ManageAppSettingsUseCase;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class AppDirectoryManager {

    private static final Logger logger = LoggerFactory.getLogger(AppDirectoryManager.class);

    @ConfigProperty(name = "sourceviewer.repos.base-path")
    Optional<String> reposBasePath;

    private final ManageAppSettingsUseCase manageAppSettings;

    @Inject
    public AppDirectoryManager(ManageAppSettingsUseCase manageAppSettings) {
        this.manageAppSettings = manageAppSettings;
    }

    public Path getReposBaseDirectory() {
        return reposBasePath
                .filter(s -> !s.isBlank())
                .map(Path::of)
                .orElse(Path.of(System.getProperty("java.io.tmpdir"), "sourceviewer-repos"));
    }

    public Path getLspWorkspaceBaseDirectory() {
        String configured = manageAppSettings.getSetting(
                ManageAppSettingsUseCase.SETTING_LSP_WORKSPACE_BASE_PATH,
                ManageAppSettingsUseCase.DEFAULT_LSP_WORKSPACE_BASE_PATH);
        if (configured == null || configured.isBlank()) {
            return Path.of(System.getProperty("user.home"), "sourceviewer-lsp-workspaces");
        }
        return Path.of(configured);
    }

    public Map<String, Path> getAllDirectories() {
        return Map.of("Git repositories", getReposBaseDirectory(),
                "JDTLS workspaces", getLspWorkspaceBaseDirectory());
    }

    void onStart(@Observes StartupEvent ev) {
        logger.info("Storage directories:");
        getAllDirectories().forEach((name, path) -> logger.info("  {} : {}", name, path));
    }
}
