package com.hlag.sourceviewer.infrastructure.lsp.jdtls;

import com.hlag.sourceviewer.domain.port.incoming.ManageAppSettingsUseCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Resolves the JDTLS config directory for the current platform, with admin overrides. */
@ApplicationScoped
public class JdtlsPlatformConfigurationResolver {

    private static final Logger logger = LoggerFactory.getLogger(JdtlsPlatformConfigurationResolver.class);

    private final ManageAppSettingsUseCase settings;

    @Inject
    public JdtlsPlatformConfigurationResolver(ManageAppSettingsUseCase settings) {
        this.settings = settings;
    }

    /** Resolves the configured JDTLS platform directory path. */
    public Path resolveConfigPath() {
        JdtlsOperatingSystem operatingSystem = resolveOperatingSystem();
        JdtlsArchitecture architecture = resolveArchitecture();

        List<String> keys = keysInPriorityOrder(operatingSystem, architecture);
        for (String key : keys) {
            String pathValue = settings.getSetting(key, "");
            if (pathValue != null && !pathValue.isBlank()) {
                Path path = Path.of(pathValue);
                if (Files.isDirectory(path)) {
                    if (!key.equals(primaryKey(operatingSystem, architecture))) {
                        logger.warn("JDTLS config key '{}' not set; falling back to '{}'", primaryKey(operatingSystem, architecture), key);
                    }
                    return path;
                }
                logger.warn("Configured JDTLS path for key '{}' is not a directory: {}", key, path);
            }
        }

        throw new IllegalStateException("No valid JDTLS config directory configured for platform "
                + operatingSystem + "/" + architecture + ". Tried keys: " + keys);
    }

    private JdtlsOperatingSystem resolveOperatingSystem() {
        String override = settings.getSetting(
                ManageAppSettingsUseCase.SETTING_LSP_JDTLS_OVERRIDE_OS,
                ManageAppSettingsUseCase.DEFAULT_LSP_JDTLS_OVERRIDE_OS);
        if (override != null && !override.isBlank()) {
            return switch (override.trim().toLowerCase()) {
                case "windows" -> JdtlsOperatingSystem.WINDOWS;
                case "linux" -> JdtlsOperatingSystem.LINUX;
                case "mac", "macos", "osx", "darwin" -> JdtlsOperatingSystem.MACOS;
                default -> throw new IllegalArgumentException("Unsupported JDTLS OS override: " + override);
            };
        }

        String detected = System.getProperty("os.name", "").toLowerCase();
        if (detected.contains("win")) {
            return JdtlsOperatingSystem.WINDOWS;
        }
        if (detected.contains("mac") || detected.contains("darwin")) {
            return JdtlsOperatingSystem.MACOS;
        }
        return JdtlsOperatingSystem.LINUX;
    }

    private JdtlsArchitecture resolveArchitecture() {
        String override = settings.getSetting(
                ManageAppSettingsUseCase.SETTING_LSP_JDTLS_OVERRIDE_ARCH,
                ManageAppSettingsUseCase.DEFAULT_LSP_JDTLS_OVERRIDE_ARCH);
        if (override != null && !override.isBlank()) {
            return switch (override.trim().toLowerCase()) {
                case "x64", "amd64", "x86_64" -> JdtlsArchitecture.X64;
                case "arm64", "aarch64" -> JdtlsArchitecture.ARM64;
                default -> throw new IllegalArgumentException("Unsupported JDTLS architecture override: " + override);
            };
        }

        String detected = System.getProperty("os.arch", "").toLowerCase();
        if (detected.contains("aarch64") || detected.contains("arm64")) {
            return JdtlsArchitecture.ARM64;
        }
        return JdtlsArchitecture.X64;
    }

    private List<String> keysInPriorityOrder(JdtlsOperatingSystem os, JdtlsArchitecture architecture) {
        List<String> keys = new ArrayList<>();
        keys.add(primaryKey(os, architecture));

        // Fallback order is explicit and visible: try x64 for the same OS before failing.
        if (architecture == JdtlsArchitecture.ARM64) {
            keys.add(primaryKey(os, JdtlsArchitecture.X64));
        }
        return keys;
    }

    private String primaryKey(JdtlsOperatingSystem os, JdtlsArchitecture architecture) {
        return switch (os) {
            case WINDOWS -> architecture == JdtlsArchitecture.ARM64
                    ? ManageAppSettingsUseCase.SETTING_LSP_JDTLS_CONFIG_WINDOWS_ARM64
                    : ManageAppSettingsUseCase.SETTING_LSP_JDTLS_CONFIG_WINDOWS_X64;
            case LINUX -> architecture == JdtlsArchitecture.ARM64
                    ? ManageAppSettingsUseCase.SETTING_LSP_JDTLS_CONFIG_LINUX_ARM64
                    : ManageAppSettingsUseCase.SETTING_LSP_JDTLS_CONFIG_LINUX_X64;
            case MACOS -> architecture == JdtlsArchitecture.ARM64
                    ? ManageAppSettingsUseCase.SETTING_LSP_JDTLS_CONFIG_MACOS_ARM64
                    : ManageAppSettingsUseCase.SETTING_LSP_JDTLS_CONFIG_MACOS_X64;
        };
    }
}

