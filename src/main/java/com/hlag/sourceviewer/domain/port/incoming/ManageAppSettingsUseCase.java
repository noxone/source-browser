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
