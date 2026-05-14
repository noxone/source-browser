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
    private static final Map<String, String> DESCRIPTIONS = Map.of(
            ManageAppSettingsUseCase.SETTING_SCAN_MAX_PARALLEL_JOBS,
            "Maximum number of scan jobs executed concurrently per application instance",
            ManageAppSettingsUseCase.SETTING_SCAN_BATCH_SIZE,
            "Number of files processed per batch during a scan (each batch runs in its own transaction)",
            ManageAppSettingsUseCase.SETTING_SCAN_CHUNK_SIZE,
            "Maximum number of characters per document chunk when indexing large files (avoids the PostgreSQL 1 MB tsvector limit)",
            ManageAppSettingsUseCase.SETTING_MAVEN_REPO_URL,
            "URL of the Maven repository used for dependency resolution (defaults to Maven Central)",
            ManageAppSettingsUseCase.SETTING_MAVEN_REPO_USERNAME,
            "Username for authenticating against the configured Maven repository (leave empty for no authentication)",
            ManageAppSettingsUseCase.SETTING_MAVEN_REPO_PASSWORD,
            "Password / secret for authenticating against the configured Maven repository"
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
                    ManageAppSettingsUseCase.DEFAULT_MAVEN_REPO_PASSWORD, true)
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
