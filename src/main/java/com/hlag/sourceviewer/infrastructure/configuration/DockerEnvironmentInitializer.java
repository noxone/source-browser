package com.hlag.sourceviewer.infrastructure.configuration;

import com.hlag.sourceviewer.domain.port.incoming.ManageAppSettingsUseCase;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * On first start inside a Docker container, auto-populates JDTLS settings that
 * are known from the image layout so the operator doesn't have to set them manually.
 *
 * <p>Detection: the file {@code /.dockerenv} is created by the Docker runtime on
 * every container start. Settings are only written when they are currently empty,
 * so any explicit admin override is never overwritten.</p>
 */
@ApplicationScoped
public class DockerEnvironmentInitializer {

    private static final Logger logger = LoggerFactory.getLogger(DockerEnvironmentInitializer.class);

    private static final Path DOCKER_ENV_MARKER = Path.of("/.dockerenv");

    private final ManageAppSettingsUseCase settings;

    @Inject
    public DockerEnvironmentInitializer(ManageAppSettingsUseCase settings) {
        this.settings = settings;
    }

    void onStart(@Observes StartupEvent event) {
        if (!Files.exists(DOCKER_ENV_MARKER)) {
            return;
        }

        String jdtlsHome = System.getenv("JDTLS_HOME");
        if (jdtlsHome == null || jdtlsHome.isBlank()) {
            logger.info("Docker environment detected, but JDTLS_HOME is not set — skipping auto-configuration");
            return;
        }

        logger.info("Docker environment detected. Auto-configuring JDTLS settings from JDTLS_HOME={}", jdtlsHome);
        Path jdtlsHomePath = Path.of(jdtlsHome);

        applyIfEmpty(ManageAppSettingsUseCase.SETTING_LSP_JDTLS_LAUNCHER_JAR,
                findLauncherJar(jdtlsHomePath));
        applyIfEmpty(ManageAppSettingsUseCase.SETTING_LSP_JDTLS_CONFIG_LINUX_X64,
                resolveConfigDir(jdtlsHomePath, "config_linux"));
        applyIfEmpty(ManageAppSettingsUseCase.SETTING_LSP_JDTLS_CONFIG_LINUX_ARM64,
                resolveConfigDir(jdtlsHomePath, "config_linux_arm"));
    }

    private String findLauncherJar(Path jdtlsHome) {
        Path pluginsDir = jdtlsHome.resolve("plugins");
        if (!Files.isDirectory(pluginsDir)) {
            logger.warn("JDTLS plugins directory not found: {}", pluginsDir);
            return null;
        }
        try (var stream = Files.find(pluginsDir, 1,
                (path, attrs) -> attrs.isRegularFile()
                        && path.getFileName().toString().startsWith("org.eclipse.equinox.launcher_")
                        && path.getFileName().toString().endsWith(".jar"))) {
            return stream.findFirst().map(Path::toString).orElse(null);
        } catch (IOException e) {
            logger.warn("Failed to scan JDTLS plugins directory {}: {}", pluginsDir, e.getMessage());
            return null;
        }
    }

    private String resolveConfigDir(Path jdtlsHome, String name) {
        Path dir = jdtlsHome.resolve(name);
        return Files.isDirectory(dir) ? dir.toString() : null;
    }

    private void applyIfEmpty(String key, String detectedValue) {
        if (detectedValue == null) {
            return;
        }
        String current = settings.getSetting(key, "");
        if (current != null && !current.isBlank()) {
            logger.debug("Setting '{}' is already configured — skipping", key);
            return;
        }
        settings.setSetting(key, detectedValue);
        logger.info("Auto-configured '{}' = '{}'", key, detectedValue);
    }
}
