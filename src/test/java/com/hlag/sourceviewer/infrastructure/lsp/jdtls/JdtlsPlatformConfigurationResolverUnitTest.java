package com.hlag.sourceviewer.infrastructure.lsp.jdtls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hlag.sourceviewer.domain.port.incoming.ManageAppSettingsUseCase;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdtlsPlatformConfigurationResolverUnitTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void uses_explicit_override_platform_key_when_configured() throws Exception {
        Path configuredDirectory = Files.createDirectories(temporaryDirectory.resolve("config-macos-arm64"));

        ManageAppSettingsUseCase settings = mock(ManageAppSettingsUseCase.class);
        when(settings.getSetting(
                ManageAppSettingsUseCase.SETTING_LSP_JDTLS_OVERRIDE_OS,
                ManageAppSettingsUseCase.DEFAULT_LSP_JDTLS_OVERRIDE_OS)).thenReturn("macos");
        when(settings.getSetting(
                ManageAppSettingsUseCase.SETTING_LSP_JDTLS_OVERRIDE_ARCH,
                ManageAppSettingsUseCase.DEFAULT_LSP_JDTLS_OVERRIDE_ARCH)).thenReturn("arm64");
        when(settings.getSetting(ManageAppSettingsUseCase.SETTING_LSP_JDTLS_CONFIG_MACOS_ARM64, ""))
                .thenReturn(configuredDirectory.toString());

        JdtlsPlatformConfigurationResolver resolver = new JdtlsPlatformConfigurationResolver(settings);

        Path resolved = resolver.resolveConfigPath();

        assertThat(resolved).isEqualTo(configuredDirectory);
    }

    @Test
    void falls_back_to_x64_when_arm64_config_is_missing() throws Exception {
        Path fallbackDirectory = Files.createDirectories(temporaryDirectory.resolve("config-linux-x64"));

        ManageAppSettingsUseCase settings = mock(ManageAppSettingsUseCase.class);
        when(settings.getSetting(
                ManageAppSettingsUseCase.SETTING_LSP_JDTLS_OVERRIDE_OS,
                ManageAppSettingsUseCase.DEFAULT_LSP_JDTLS_OVERRIDE_OS)).thenReturn("linux");
        when(settings.getSetting(
                ManageAppSettingsUseCase.SETTING_LSP_JDTLS_OVERRIDE_ARCH,
                ManageAppSettingsUseCase.DEFAULT_LSP_JDTLS_OVERRIDE_ARCH)).thenReturn("arm64");
        when(settings.getSetting(ManageAppSettingsUseCase.SETTING_LSP_JDTLS_CONFIG_LINUX_ARM64, ""))
                .thenReturn("");
        when(settings.getSetting(ManageAppSettingsUseCase.SETTING_LSP_JDTLS_CONFIG_LINUX_X64, ""))
                .thenReturn(fallbackDirectory.toString());

        JdtlsPlatformConfigurationResolver resolver = new JdtlsPlatformConfigurationResolver(settings);

        Path resolved = resolver.resolveConfigPath();

        assertThat(resolved).isEqualTo(fallbackDirectory);
    }
}

