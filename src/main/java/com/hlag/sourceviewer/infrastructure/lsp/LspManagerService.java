package com.hlag.sourceviewer.infrastructure.lsp;

import com.hlag.sourceviewer.application.scan.lsp.LanguageServerSession;
import com.hlag.sourceviewer.application.scan.lsp.LspManager;
import com.hlag.sourceviewer.application.scan.lsp.LspProjectContext;
import com.hlag.sourceviewer.domain.port.incoming.ManageAppSettingsUseCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/** Default {@link LspManager} implementation that delegates startup to language-specific providers. */
@ApplicationScoped
public class LspManagerService implements LspManager {

    private final Instance<LanguageServerProvider> providers;
    private final DefaultLspReadinessStrategy defaultReadinessStrategy;
    private final ManageAppSettingsUseCase manageAppSettingsUseCase;

    @Inject
    public LspManagerService(
            Instance<LanguageServerProvider> providers,
            DefaultLspReadinessStrategy defaultReadinessStrategy,
            ManageAppSettingsUseCase manageAppSettingsUseCase) {
        this.providers = providers;
        this.defaultReadinessStrategy = defaultReadinessStrategy;
        this.manageAppSettingsUseCase = manageAppSettingsUseCase;
    }

    /** @inheritDoc */
    @Override
    public LanguageServerSession getLspForLanguage(String language, LspProjectContext context) {
        LanguageServerProvider provider = findProvider(language)
                .orElseThrow(() -> new NoSuchElementException("No language-server provider found for language: " + language));

        LanguageServerSession session;
        try {
            session = provider.startSession(context);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to start language server for language: " + language, exception);
        }

        Duration timeout = Duration.ofMillis(Long.parseLong(manageAppSettingsUseCase.getSetting(
                ManageAppSettingsUseCase.SETTING_LSP_DEFAULT_READY_TIMEOUT_MS,
                ManageAppSettingsUseCase.DEFAULT_LSP_DEFAULT_READY_TIMEOUT_MS)));
        LspReadinessStrategy strategy = provider.readinessStrategy().orElse(defaultReadinessStrategy);
        strategy.waitUntilReady(session, context, timeout);
        return session;
    }

    private Optional<LanguageServerProvider> findProvider(String language) {
        List<LanguageServerProvider> available = java.util.stream.StreamSupport
                .stream(providers.spliterator(), false)
                .toList();
        return available.stream().filter(provider -> provider.supportedLanguage().equals(language)).findFirst();
    }
}

