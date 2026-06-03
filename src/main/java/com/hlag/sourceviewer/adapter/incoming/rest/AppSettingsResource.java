package com.hlag.sourceviewer.adapter.incoming.rest;

import com.hlag.sourceviewer.adapter.incoming.rest.dto.AppSettingDto;
import com.hlag.sourceviewer.domain.port.incoming.ManageAppSettingsUseCase;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

/**
 * REST resource for managing runtime application settings.
 *
 * <p>All settings known to the application are always listed (with their default value
 * if not yet stored in the database). Individual settings can be updated via PUT.</p>
 */
@Path("/api/admin/settings")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class AppSettingsResource {

    private static final String MASKED = "****";

    /** Descriptions for all known settings, shown in the UI. */
    private static final Map<String, String> DESCRIPTIONS = Map.ofEntries(
            Map.entry(ManageAppSettingsUseCase.SETTING_SCAN_MAX_PARALLEL_JOBS,
                    "Maximum number of scan jobs executed concurrently per application instance"),
            Map.entry(ManageAppSettingsUseCase.SETTING_SCAN_BATCH_SIZE,
                    "Number of files processed per batch during a scan (each batch runs in its own transaction)"),
            Map.entry(ManageAppSettingsUseCase.SETTING_SCAN_CHUNK_SIZE,
                    "Maximum number of characters per document chunk when indexing large files (avoids the PostgreSQL 1 MB tsvector limit)"),
            Map.entry(ManageAppSettingsUseCase.SETTING_MAVEN_REPO_URL,
                    "URL of the Maven repository used for dependency resolution (defaults to Maven Central)"),
            Map.entry(ManageAppSettingsUseCase.SETTING_MAVEN_REPO_USERNAME,
                    "Username for authenticating against the configured Maven repository (leave empty for no authentication)"),
            Map.entry(ManageAppSettingsUseCase.SETTING_MAVEN_REPO_PASSWORD,
                    "Password / secret for authenticating against the configured Maven repository"),
            Map.entry(ManageAppSettingsUseCase.SETTING_LSP_WORKSPACE_BASE_PATH,
                    "Base directory for persistent language-server workspaces (leave empty to use the user-home default)"),
            Map.entry(ManageAppSettingsUseCase.SETTING_LSP_DEFAULT_READY_TIMEOUT_MS,
                    "Default timeout in milliseconds while waiting for a language server to become ready"),
            Map.entry(ManageAppSettingsUseCase.SETTING_LSP_JDTLS_COMMAND,
                    "Java command used to launch JDTLS (java executable or absolute path)"),
            Map.entry(ManageAppSettingsUseCase.SETTING_LSP_JDTLS_LAUNCHER_JAR,
                    "Absolute path to the JDTLS org.eclipse.equinox.launcher_*.jar file"),
            Map.entry(ManageAppSettingsUseCase.SETTING_LSP_JDTLS_OVERRIDE_OS,
                    "Optional explicit OS override for JDTLS platform resolution (windows/linux/macos)"),
            Map.entry(ManageAppSettingsUseCase.SETTING_LSP_JDTLS_OVERRIDE_ARCH,
                    "Optional explicit architecture override for JDTLS platform resolution (x64/arm64)"),
            Map.entry(ManageAppSettingsUseCase.SETTING_LSP_JDTLS_CONFIG_WINDOWS_X64,
                    "Absolute path of the JDTLS config directory for Windows x64"),
            Map.entry(ManageAppSettingsUseCase.SETTING_LSP_JDTLS_CONFIG_WINDOWS_ARM64,
                    "Absolute path of the JDTLS config directory for Windows arm64"),
            Map.entry(ManageAppSettingsUseCase.SETTING_LSP_JDTLS_CONFIG_LINUX_X64,
                    "Absolute path of the JDTLS config directory for Linux x64"),
            Map.entry(ManageAppSettingsUseCase.SETTING_LSP_JDTLS_CONFIG_LINUX_ARM64,
                    "Absolute path of the JDTLS config directory for Linux arm64"),
            Map.entry(ManageAppSettingsUseCase.SETTING_LSP_JDTLS_CONFIG_MACOS_X64,
                    "Absolute path of the JDTLS config directory for macOS x64"),
            Map.entry(ManageAppSettingsUseCase.SETTING_LSP_JDTLS_CONFIG_MACOS_ARM64,
                    "Absolute path of the JDTLS config directory for macOS arm64"),
            Map.entry(ManageAppSettingsUseCase.SETTING_LSP_JDTLS_MAVEN_LOCAL_REPO,
                    "Absolute path to a custom Maven local repository for JDTLS to use (leave empty to use ~/.m2/repository)"),
            Map.entry(ManageAppSettingsUseCase.SETTING_SCHEDULER_SYNC_GROUPS_CRON,
                    "Cron expression for the daily group-sync job (Quarkus/Quartz format, e.g. \"0 0 2 * * ?\")"),
            Map.entry(ManageAppSettingsUseCase.SETTING_SCHEDULER_DETECT_CHANGES_CRON,
                    "Cron expression for the repository change-detection job (Quarkus/Quartz format, e.g. \"0 0 * * * ?\")")
    );

    /** Known settings with their default values, in display order. */
    private static final List<KnownSetting> KNOWN_SETTINGS = List.of(
            new KnownSetting(
                    ManageAppSettingsUseCase.SETTING_SCAN_MAX_PARALLEL_JOBS,
                    ManageAppSettingsUseCase.DEFAULT_SCAN_MAX_PARALLEL_JOBS, false),
            new KnownSetting(
                    ManageAppSettingsUseCase.SETTING_SCAN_BATCH_SIZE,
                    ManageAppSettingsUseCase.DEFAULT_SCAN_BATCH_SIZE, false),
            new KnownSetting(
                    ManageAppSettingsUseCase.SETTING_SCAN_CHUNK_SIZE,
                    ManageAppSettingsUseCase.DEFAULT_SCAN_CHUNK_SIZE, false),
            new KnownSetting(
                    ManageAppSettingsUseCase.SETTING_MAVEN_REPO_URL,
                    ManageAppSettingsUseCase.DEFAULT_MAVEN_REPO_URL, false),
            new KnownSetting(
                    ManageAppSettingsUseCase.SETTING_MAVEN_REPO_USERNAME,
                    ManageAppSettingsUseCase.DEFAULT_MAVEN_REPO_USERNAME, false),
            new KnownSetting(
                    ManageAppSettingsUseCase.SETTING_MAVEN_REPO_PASSWORD,
                    ManageAppSettingsUseCase.DEFAULT_MAVEN_REPO_PASSWORD, true),
            new KnownSetting(
                    ManageAppSettingsUseCase.SETTING_LSP_WORKSPACE_BASE_PATH,
                    ManageAppSettingsUseCase.DEFAULT_LSP_WORKSPACE_BASE_PATH, false),
            new KnownSetting(
                    ManageAppSettingsUseCase.SETTING_LSP_DEFAULT_READY_TIMEOUT_MS,
                    ManageAppSettingsUseCase.DEFAULT_LSP_DEFAULT_READY_TIMEOUT_MS, false),
            new KnownSetting(
                    ManageAppSettingsUseCase.SETTING_LSP_JDTLS_COMMAND,
                    ManageAppSettingsUseCase.DEFAULT_LSP_JDTLS_COMMAND, false),
            new KnownSetting(
                    ManageAppSettingsUseCase.SETTING_LSP_JDTLS_LAUNCHER_JAR,
                    ManageAppSettingsUseCase.DEFAULT_LSP_JDTLS_LAUNCHER_JAR, false),
            new KnownSetting(
                    ManageAppSettingsUseCase.SETTING_LSP_JDTLS_OVERRIDE_OS,
                    ManageAppSettingsUseCase.DEFAULT_LSP_JDTLS_OVERRIDE_OS, false),
            new KnownSetting(
                    ManageAppSettingsUseCase.SETTING_LSP_JDTLS_OVERRIDE_ARCH,
                    ManageAppSettingsUseCase.DEFAULT_LSP_JDTLS_OVERRIDE_ARCH, false),
            new KnownSetting(
                    ManageAppSettingsUseCase.SETTING_LSP_JDTLS_CONFIG_WINDOWS_X64,
                    ManageAppSettingsUseCase.DEFAULT_LSP_JDTLS_CONFIG_WINDOWS_X64, false),
            new KnownSetting(
                    ManageAppSettingsUseCase.SETTING_LSP_JDTLS_CONFIG_WINDOWS_ARM64,
                    ManageAppSettingsUseCase.DEFAULT_LSP_JDTLS_CONFIG_WINDOWS_ARM64, false),
            new KnownSetting(
                    ManageAppSettingsUseCase.SETTING_LSP_JDTLS_CONFIG_LINUX_X64,
                    ManageAppSettingsUseCase.DEFAULT_LSP_JDTLS_CONFIG_LINUX_X64, false),
            new KnownSetting(
                    ManageAppSettingsUseCase.SETTING_LSP_JDTLS_CONFIG_LINUX_ARM64,
                    ManageAppSettingsUseCase.DEFAULT_LSP_JDTLS_CONFIG_LINUX_ARM64, false),
            new KnownSetting(
                    ManageAppSettingsUseCase.SETTING_LSP_JDTLS_CONFIG_MACOS_X64,
                    ManageAppSettingsUseCase.DEFAULT_LSP_JDTLS_CONFIG_MACOS_X64, false),
            new KnownSetting(
                    ManageAppSettingsUseCase.SETTING_LSP_JDTLS_CONFIG_MACOS_ARM64,
                    ManageAppSettingsUseCase.DEFAULT_LSP_JDTLS_CONFIG_MACOS_ARM64, false),
            new KnownSetting(
                    ManageAppSettingsUseCase.SETTING_LSP_JDTLS_MAVEN_LOCAL_REPO,
                    ManageAppSettingsUseCase.DEFAULT_LSP_JDTLS_MAVEN_LOCAL_REPO, false),
            new KnownSetting(
                    ManageAppSettingsUseCase.SETTING_SCHEDULER_SYNC_GROUPS_CRON,
                    ManageAppSettingsUseCase.DEFAULT_SCHEDULER_SYNC_GROUPS_CRON, false),
            new KnownSetting(
                    ManageAppSettingsUseCase.SETTING_SCHEDULER_DETECT_CHANGES_CRON,
                    ManageAppSettingsUseCase.DEFAULT_SCHEDULER_DETECT_CHANGES_CRON, false)
    );

    private record KnownSetting(String key, String defaultValue, boolean secret) {}

    private final ManageAppSettingsUseCase manageAppSettingsUseCase;

    @Inject
    public AppSettingsResource(ManageAppSettingsUseCase manageAppSettingsUseCase) {
        this.manageAppSettingsUseCase = manageAppSettingsUseCase;
    }

    /**
     * Returns all known settings with their current values (or defaults if not yet set).
     */
    @GET
    public List<AppSettingDto> listSettings() {
        return KNOWN_SETTINGS.stream()
                .map(known -> {
                    String stored = manageAppSettingsUseCase.getSetting(known.key(), known.defaultValue());
                    String value = known.secret() && !stored.isEmpty() ? MASKED : stored;
                    String description = DESCRIPTIONS.getOrDefault(known.key(), "");
                    return new AppSettingDto(known.key(), value, description, known.secret());
                })
                .toList();
    }

    /**
     * Updates the value of a single setting.
     *
     * @param key   the setting key
     * @param body  request body with a {@code "value"} field
     * @return 204 No Content on success, 400 if the key is unknown
     */
    @PUT
    @Path("/{key}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateSetting(
            @PathParam("key") String key,
            AppSettingDto body) {

        KnownSetting known = KNOWN_SETTINGS.stream().filter(k -> k.key().equals(key)).findFirst().orElse(null);
        if (known == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Unknown setting key: " + key + "\"}")
                    .build();
        }

        // Ignore the masked sentinel — the client is echoing back a value it cannot see
        if (known.secret() && MASKED.equals(body.value())) {
            return Response.noContent().build();
        }

        manageAppSettingsUseCase.setSetting(key, body.value());
        return Response.noContent().build();
    }
}
