package com.hlag.sourceviewer.infrastructure.lsp.jdtls;

import com.hlag.sourceviewer.application.scan.lsp.LanguageServerSession;
import com.hlag.sourceviewer.application.scan.lsp.LspProjectContext;
import com.hlag.sourceviewer.domain.port.incoming.ManageAppSettingsUseCase;
import com.hlag.sourceviewer.domain.port.outgoing.LanguageServerWorkspaceStore;
import com.hlag.sourceviewer.infrastructure.lsp.LanguageServerProvider;
import com.hlag.sourceviewer.infrastructure.lsp.LspReadinessStrategy;
import com.hlag.sourceviewer.infrastructure.lsp.ProcessBackedLanguageServerSession;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageServer;

/** Starts and initializes JDTLS as an external process for Java language-server features. */
@ApplicationScoped
public class JdtlsLanguageServerProvider implements LanguageServerProvider {

    private final ManageAppSettingsUseCase settings;
    private final JdtlsPlatformConfigurationResolver platformConfigurationResolver;
    private final LanguageServerWorkspaceStore workspaceStore;
    private final JdtlsReadinessStrategy jdtlsReadinessStrategy;

    @Inject
    public JdtlsLanguageServerProvider(
            ManageAppSettingsUseCase settings,
            JdtlsPlatformConfigurationResolver platformConfigurationResolver,
            LanguageServerWorkspaceStore workspaceStore,
            JdtlsReadinessStrategy jdtlsReadinessStrategy) {
        this.settings = settings;
        this.platformConfigurationResolver = platformConfigurationResolver;
        this.workspaceStore = workspaceStore;
        this.jdtlsReadinessStrategy = jdtlsReadinessStrategy;
    }

    @Override
    public String supportedLanguage() {
        return "java";
    }

    /**
     * Returns the JDTLS-specific readiness strategy that waits for the
     * {@code language/status} {@code ServiceReady} event rather than polling.
     */
    @Override
    public Optional<LspReadinessStrategy> readinessStrategy() {
        return Optional.of(jdtlsReadinessStrategy);
    }

    @Override
    public LanguageServerSession startSession(LspProjectContext context) throws IOException {
        Path workspacePath = workspaceStore.resolveWorkspacePath(context.repository());
        Process process = startProcess(context, workspacePath);

        var client = new JdtlsNotifyingLanguageClient();
        Launcher<LanguageServer> launcher = LSPLauncher.createClientLauncher(
                client,
                process.getInputStream(),
                process.getOutputStream());

        var listeningFuture = launcher.startListening();
        LanguageServer languageServer = launcher.getRemoteProxy();

        initializeServer(languageServer, context);

        return new ProcessBackedLanguageServerSession(
                supportedLanguage(),
                context.projectRoot(),
                workspacePath,
                languageServer,
                process,
                listeningFuture,
                Optional.of(client.serviceReadyFuture()));
    }

    private Process startProcess(LspProjectContext context, Path workspacePath) throws IOException {
        String javaCommand = settings.getSetting(
                ManageAppSettingsUseCase.SETTING_LSP_JDTLS_COMMAND,
                ManageAppSettingsUseCase.DEFAULT_LSP_JDTLS_COMMAND);
        String launcherJar = settings.getSetting(
                ManageAppSettingsUseCase.SETTING_LSP_JDTLS_LAUNCHER_JAR,
                ManageAppSettingsUseCase.DEFAULT_LSP_JDTLS_LAUNCHER_JAR);
        if (launcherJar == null || launcherJar.isBlank()) {
            throw new IllegalStateException("JDTLS launcher JAR is not configured: "
                    + ManageAppSettingsUseCase.SETTING_LSP_JDTLS_LAUNCHER_JAR);
        }

        Path configPath = platformConfigurationResolver.resolveConfigPath();

        List<String> command = new ArrayList<>();
        command.add(javaCommand);
        command.add(mavenSystemProperty(ManageAppSettingsUseCase.SETTING_MAVEN_REPO_URL,
                ManageAppSettingsUseCase.DEFAULT_MAVEN_REPO_URL,
                "sourceviewer.maven.repository.url"));
        command.add(mavenSystemProperty(ManageAppSettingsUseCase.SETTING_MAVEN_REPO_USERNAME,
                ManageAppSettingsUseCase.DEFAULT_MAVEN_REPO_USERNAME,
                "sourceviewer.maven.repository.username"));
        command.add(mavenSystemProperty(ManageAppSettingsUseCase.SETTING_MAVEN_REPO_PASSWORD,
                ManageAppSettingsUseCase.DEFAULT_MAVEN_REPO_PASSWORD,
                "sourceviewer.maven.repository.password"));
        command.add("-jar");
        command.add(launcherJar);
        command.add("-configuration");
        command.add(configPath.toString());
        command.add("-data");
        command.add(workspacePath.toString());

        var processBuilder = new ProcessBuilder(command);
        processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);
        processBuilder.directory(context.projectRoot().toFile());
        return processBuilder.start();
    }

    private String mavenSystemProperty(String settingKey, String defaultValue, String propertyName) {
        String value = settings.getSetting(settingKey, defaultValue);
        return "-D" + propertyName + "=" + (value == null ? "" : value);
    }

    private static void initializeServer(LanguageServer languageServer, LspProjectContext context) {
        InitializeParams params = new InitializeParams();
        params.setRootUri(context.projectRoot().toUri().toString());
        params.setWorkspaceFolders(List.of(new WorkspaceFolder(
                context.projectRoot().toUri().toString(),
                context.repository().name().value())));

        ClientCapabilities capabilities = new ClientCapabilities();
        WorkspaceClientCapabilities workspaceCapabilities = new WorkspaceClientCapabilities();
        capabilities.setWorkspace(workspaceCapabilities);
        params.setCapabilities(capabilities);

        try {
            InitializeResult result = languageServer.initialize(params).get(30, TimeUnit.SECONDS);
            if (result == null) {
                throw new IllegalStateException("JDTLS initialize returned no result");
            }
            languageServer.initialized(new InitializedParams());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to initialize JDTLS language server", exception);
        }
    }
}
