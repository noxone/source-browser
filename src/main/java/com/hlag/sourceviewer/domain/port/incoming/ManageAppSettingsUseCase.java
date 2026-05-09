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
