package com.hlag.sourceviewer.domain.port.incoming;

import com.hlag.sourceviewer.domain.model.setting.AppSetting;

import java.util.List;

/**
 * Use case for managing runtime-configurable application settings.
 *
 * <p>Settings are stored in the database and can be changed without restarting
 * the application. Known settings with their defaults are defined as constants.</p>
 */
public interface ManageAppSettingsUseCase {

    // ── Known setting keys ────────────────────────────────────────────────────

    /** Maximum number of scan jobs executed concurrently per application instance. */
    String SETTING_SCAN_MAX_PARALLEL_JOBS = "scan.max-parallel-jobs";

    /** Default value for {@link #SETTING_SCAN_MAX_PARALLEL_JOBS}. */
    String DEFAULT_SCAN_MAX_PARALLEL_JOBS = "2";

    /** Number of files processed per batch during a scan (each batch runs in its own transaction). */
    String SETTING_SCAN_BATCH_SIZE = "scan.batch-size";

    /** Default value for {@link #SETTING_SCAN_BATCH_SIZE}. */
    String DEFAULT_SCAN_BATCH_SIZE = "200";

    /** Maximum characters per document chunk when splitting large files for full-text indexing. */
    String SETTING_SCAN_CHUNK_SIZE = "scan.chunk-size";

    /** Default value for {@link #SETTING_SCAN_CHUNK_SIZE}: 500,000 characters. */
    String DEFAULT_SCAN_CHUNK_SIZE = "500000";

    /** URL of the Maven repository used for dependency resolution. */
    String SETTING_MAVEN_REPO_URL = "maven.repository.url";

    /** Default value for {@link #SETTING_MAVEN_REPO_URL}: Maven Central. */
    String DEFAULT_MAVEN_REPO_URL = "https://repo.maven.apache.org/maven2/";

    /** Username for authenticating against the configured Maven repository (empty = no auth). */
    String SETTING_MAVEN_REPO_USERNAME = "maven.repository.username";

    /** Default value for {@link #SETTING_MAVEN_REPO_USERNAME}. */
    String DEFAULT_MAVEN_REPO_USERNAME = "";

    /** Password / secret for authenticating against the configured Maven repository. */
    String SETTING_MAVEN_REPO_PASSWORD = "maven.repository.password";

    /** Default value for {@link #SETTING_MAVEN_REPO_PASSWORD}. */
    String DEFAULT_MAVEN_REPO_PASSWORD = "";

    /** Base path where persistent language-server workspaces are stored. */
    String SETTING_LSP_WORKSPACE_BASE_PATH = "lsp.workspace.base-path";

    /** Default value for {@link #SETTING_LSP_WORKSPACE_BASE_PATH}. */
    String DEFAULT_LSP_WORKSPACE_BASE_PATH = "";

    /** Default timeout in milliseconds for waiting until a language server is ready. */
    String SETTING_LSP_DEFAULT_READY_TIMEOUT_MS = "lsp.default-ready-timeout-ms";

    /** Default value for {@link #SETTING_LSP_DEFAULT_READY_TIMEOUT_MS}. */
    String DEFAULT_LSP_DEFAULT_READY_TIMEOUT_MS = "120000";

    /** Java command used to launch JDTLS (for example {@code java} or an absolute path). */
    String SETTING_LSP_JDTLS_COMMAND = "lsp.jdtls.command";

    /** Default value for {@link #SETTING_LSP_JDTLS_COMMAND}. */
    String DEFAULT_LSP_JDTLS_COMMAND = "java";

    /** Absolute path to the JDTLS launcher JAR (org.eclipse.equinox.launcher_*.jar). */
    String SETTING_LSP_JDTLS_LAUNCHER_JAR = "lsp.jdtls.launcher-jar";

    /** Default value for {@link #SETTING_LSP_JDTLS_LAUNCHER_JAR}. */
    String DEFAULT_LSP_JDTLS_LAUNCHER_JAR = "";

    /** Optional explicit operating-system override for JDTLS platform selection (windows/linux/macos). */
    String SETTING_LSP_JDTLS_OVERRIDE_OS = "lsp.jdtls.override.os";

    /** Default value for {@link #SETTING_LSP_JDTLS_OVERRIDE_OS}. */
    String DEFAULT_LSP_JDTLS_OVERRIDE_OS = "";

    /** Optional explicit architecture override for JDTLS platform selection (x64/arm64). */
    String SETTING_LSP_JDTLS_OVERRIDE_ARCH = "lsp.jdtls.override.arch";

    /** Default value for {@link #SETTING_LSP_JDTLS_OVERRIDE_ARCH}. */
    String DEFAULT_LSP_JDTLS_OVERRIDE_ARCH = "";

    /** JDTLS config directory for Windows x64. */
    String SETTING_LSP_JDTLS_CONFIG_WINDOWS_X64 = "lsp.jdtls.config.windows.x64";

    /** Default value for {@link #SETTING_LSP_JDTLS_CONFIG_WINDOWS_X64}. */
    String DEFAULT_LSP_JDTLS_CONFIG_WINDOWS_X64 = "";

    /** JDTLS config directory for Windows arm64. */
    String SETTING_LSP_JDTLS_CONFIG_WINDOWS_ARM64 = "lsp.jdtls.config.windows.arm64";

    /** Default value for {@link #SETTING_LSP_JDTLS_CONFIG_WINDOWS_ARM64}. */
    String DEFAULT_LSP_JDTLS_CONFIG_WINDOWS_ARM64 = "";

    /** JDTLS config directory for Linux x64. */
    String SETTING_LSP_JDTLS_CONFIG_LINUX_X64 = "lsp.jdtls.config.linux.x64";

    /** Default value for {@link #SETTING_LSP_JDTLS_CONFIG_LINUX_X64}. */
    String DEFAULT_LSP_JDTLS_CONFIG_LINUX_X64 = "";

    /** JDTLS config directory for Linux arm64. */
    String SETTING_LSP_JDTLS_CONFIG_LINUX_ARM64 = "lsp.jdtls.config.linux.arm64";

    /** Default value for {@link #SETTING_LSP_JDTLS_CONFIG_LINUX_ARM64}. */
    String DEFAULT_LSP_JDTLS_CONFIG_LINUX_ARM64 = "";

    /** JDTLS config directory for macOS x64. */
    String SETTING_LSP_JDTLS_CONFIG_MACOS_X64 = "lsp.jdtls.config.macos.x64";

    /** Default value for {@link #SETTING_LSP_JDTLS_CONFIG_MACOS_X64}. */
    String DEFAULT_LSP_JDTLS_CONFIG_MACOS_X64 = "";

    /** JDTLS config directory for macOS arm64. */
    String SETTING_LSP_JDTLS_CONFIG_MACOS_ARM64 = "lsp.jdtls.config.macos.arm64";

    /** Default value for {@link #SETTING_LSP_JDTLS_CONFIG_MACOS_ARM64}. */
    String DEFAULT_LSP_JDTLS_CONFIG_MACOS_ARM64 = "";

    // ── Operations ────────────────────────────────────────────────────────────

    /**
     * Returns all settings currently stored in the database.
     * Settings that have never been set will not appear in this list
     * (they use their default values).
     */
    List<AppSetting> listSettings();

    /**
     * Returns the value of the setting with the given key, or {@code defaultValue}
     * if the setting has not been configured.
     *
     * @param key          the setting key
     * @param defaultValue the value to return when no setting exists for this key
     */
    String getSetting(String key, String defaultValue);

    /**
     * Persists a setting value. Creates the setting if it does not exist,
     * updates it otherwise.
     *
     * @param key   the setting key
     * @param value the new value
     */
    void setSetting(String key, String value);
}
