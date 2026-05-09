package com.hlag.sourceviewer.domain.port.outgoing;

import com.hlag.sourceviewer.domain.model.setting.AppSetting;

import java.util.List;
import java.util.Optional;

/**
 * Port for accessing persisted application settings.
 */
public interface SettingsRepository {

    Optional<AppSetting> findByKey(String key);

    List<AppSetting> findAllSettings();

    /** Inserts or updates a setting. */
    void upsert(String key, String value);
}
