package com.hlag.sourceviewer.application.settings;

import com.hlag.sourceviewer.domain.model.setting.AppSetting;
import com.hlag.sourceviewer.domain.model.setting.SettingChangedEvent;
import com.hlag.sourceviewer.domain.port.incoming.ManageAppSettingsUseCase;
import com.hlag.sourceviewer.domain.port.outgoing.SettingsRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Implementation of {@link ManageAppSettingsUseCase}.
 *
 * <p>Reads and writes settings to the database. When a setting has not been
 * explicitly stored, the caller-provided default value is used.</p>
 */
@ApplicationScoped
public class ManageAppSettingsService implements ManageAppSettingsUseCase {

    private static final Logger logger = LoggerFactory.getLogger(ManageAppSettingsService.class);

    private final SettingsRepository settingsRepository;
    private final Event<SettingChangedEvent> settingChangedEvent;

    @Inject
    public ManageAppSettingsService(SettingsRepository settingsRepository,
                                    Event<SettingChangedEvent> settingChangedEvent) {
        this.settingsRepository = settingsRepository;
        this.settingChangedEvent = settingChangedEvent;
    }

    @Override
    public List<AppSetting> listSettings() {
        return settingsRepository.findAllSettings();
    }

    @Override
    public String getSetting(String key, String defaultValue) {
        return settingsRepository.findByKey(key)
                .map(AppSetting::value)
                .orElse(defaultValue);
    }

    @Override
    @Transactional
    public void setSetting(String key, String value) {
        settingsRepository.upsert(key, value);
        settingChangedEvent.fire(new SettingChangedEvent(key, value));
        logger.info("Setting '{}' updated to '{}'", key, value);
    }
}
