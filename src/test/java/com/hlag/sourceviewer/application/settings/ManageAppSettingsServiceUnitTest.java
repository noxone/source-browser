package com.hlag.sourceviewer.application.settings;

import com.hlag.sourceviewer.domain.model.setting.AppSetting;
import com.hlag.sourceviewer.domain.model.setting.SettingChangedEvent;
import com.hlag.sourceviewer.domain.port.outgoing.SettingsRepository;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ManageAppSettingsService}.
 */
class ManageAppSettingsServiceUnitTest {

    private SettingsRepository settingsRepository;
    @SuppressWarnings("unchecked")
    private Event<SettingChangedEvent> settingChangedEvent = mock(Event.class);
    private ManageAppSettingsService service;

    @BeforeEach
    void setUp() {
        settingsRepository = mock(SettingsRepository.class);
        service = new ManageAppSettingsService(settingsRepository, settingChangedEvent);
    }

    // ── listSettings ──────────────────────────────────────────────────────────

    @Test
    void listSettings_returns_all_settings_from_repository() {
        var setting = new AppSetting("scan.max-parallel-jobs", "4");
        when(settingsRepository.findAllSettings()).thenReturn(List.of(setting));

        var result = service.listSettings();

        assertThat(result).containsExactly(setting);
    }

    @Test
    void listSettings_returns_empty_list_when_no_settings_exist() {
        when(settingsRepository.findAllSettings()).thenReturn(List.of());

        assertThat(service.listSettings()).isEmpty();
    }

    // ── getSetting ────────────────────────────────────────────────────────────

    @Test
    void getSetting_returns_stored_value_when_key_exists() {
        when(settingsRepository.findByKey("scan.max-parallel-jobs"))
                .thenReturn(Optional.of(new AppSetting("scan.max-parallel-jobs", "5")));

        String result = service.getSetting("scan.max-parallel-jobs", "2");

        assertThat(result).isEqualTo("5");
    }

    @Test
    void getSetting_returns_default_value_when_key_not_stored() {
        when(settingsRepository.findByKey("scan.max-parallel-jobs"))
                .thenReturn(Optional.empty());

        String result = service.getSetting("scan.max-parallel-jobs", "2");

        assertThat(result).isEqualTo("2");
    }

    @Test
    void getSetting_delegates_to_repository_with_the_given_key() {
        when(settingsRepository.findByKey("some.key")).thenReturn(Optional.empty());

        service.getSetting("some.key", "default");

        verify(settingsRepository).findByKey("some.key");
    }

    // ── setSetting ────────────────────────────────────────────────────────────

    @Test
    void setSetting_delegates_to_repository_upsert() {
        service.setSetting("scan.max-parallel-jobs", "3");

        verify(settingsRepository).upsert("scan.max-parallel-jobs", "3");
    }
}
