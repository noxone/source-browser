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
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Starts and initializes JDTLS as an external process for Java language-server features. */
@ApplicationScoped
public class JdtlsLanguageServerProvider implements LanguageServerProvider {

    private static final Logger logger = LoggerFactory.getLogger(JdtlsLanguageServerProvider.class);

    static final String STDERR_LOG_FILE_NAME = "jdtls-stderr.log";

    private final ManageAppSettingsUseCase settings;
    private final JdtlsPlatformConfigurationResolver platformConfigurationResolver;
    private final LanguageServerWorkspaceStore workspaceStore;
    private final JdtlsReadinessStrategy jdtlsReadinessStrategy;
    private final JdtlsMavenSettingsWriter mavenSettingsWriter;

    @Inject
    public JdtlsLanguageServerProvider(
            ManageAppSettingsUseCase settings,
            JdtlsPlatformConfigurationResolver platformConfigurationResolver,
            LanguageServerWorkspaceStore workspaceStore,
            JdtlsReadinessStrategy jdtlsReadinessStrategy,
            JdtlsMavenSettingsWriter mavenSettingsWriter) {
        this.settings = settings;
        this.platformConfigurationResolver = platformConfigurationResolver;
        this.workspaceStore = workspaceStore;
        this.jdtlsReadinessStrategy = jdtlsReadinessStrategy;
        this.mavenSettingsWriter = mavenSettingsWriter;
    }

    /** @inheritDoc */
    @Override
    public String supportedLanguage() {
        return "java";
    }

    /**
     * Returns the JDTLS-specific readiness strategy that waits for the
     * {@code language/status} {@code ServiceReady} event and subsequent build-job quiescence.
     */
    @Override
    public Optional<LspReadinessStrategy> readinessStrategy() {
        return Optional.of(jdtlsReadinessStrategy);
    }

    /** @inheritDoc */
    @Override
    public LanguageServerSession startSession(LspProjectContext context) throws IOException {
        Path workspacePath = workspaceStore.resolveWorkspacePath(context.repository());
        Path mavenSettingsPath = writeMavenSettings(workspacePath);
        Process process = startProcess(context, workspacePath, mavenSettingsPath);

        var client = new JdtlsNotifyingLanguageClient();
        Launcher<LanguageServer> launcher = LSPLauncher.createClientLauncher(
                client,
                process.getInputStream(),
                process.getOutputStream());

        var listeningFuture = launcher.startListening();
        LanguageServer languageServer = launcher.getRemoteProxy();

        initializeServer(languageServer, context, mavenSettingsPath);

        return new ProcessBackedLanguageServerSession(
                supportedLanguage(),
                context.projectRoot(),
                workspacePath,
                languageServer,
                process,
                listeningFuture,
                Optional.of(client.serviceReadyFuture()),
                client
        );
    }

    private Path writeMavenSettings(Path workspacePath) throws IOException {
        String repoUrl = settings.getSetting(
                ManageAppSettingsUseCase.SETTING_MAVEN_REPO_URL,
                ManageAppSettingsUseCase.DEFAULT_MAVEN_REPO_URL);
        String username = settings.getSetting(
                ManageAppSettingsUseCase.SETTING_MAVEN_REPO_USERNAME,
                ManageAppSettingsUseCase.DEFAULT_MAVEN_REPO_USERNAME);
        String password = settings.getSetting(
                ManageAppSettingsUseCase.SETTING_MAVEN_REPO_PASSWORD,
                ManageAppSettingsUseCase.DEFAULT_MAVEN_REPO_PASSWORD);
        String localRepo = settings.getSetting(
                ManageAppSettingsUseCase.SETTING_LSP_JDTLS_MAVEN_LOCAL_REPO,
                ManageAppSettingsUseCase.DEFAULT_LSP_JDTLS_MAVEN_LOCAL_REPO);
        return mavenSettingsWriter.write(workspacePath, repoUrl, username, password, localRepo);
    }

    private Process startProcess(LspProjectContext context, Path workspacePath, Path mavenSettingsPath) throws IOException {
        String javaCommand = settings.getSetting(
                ManageAppSettingsUseCase.SETTING_LSP_JDTLS_COMMAND,
                ManageAppSettingsUseCase.DEFAULT_LSP_JDTLS_COMMAND);
        String launcherJar = settings.getSetting(
                ManageAppSettingsUseCase.SETTING_LSP_JDTLS_LAUNCHER_JAR,
                ManageAppSettingsUseCase.DEFAULT_LSP_JDTLS_LAUNCHER_JAR);
        String localRepo = settings.getSetting(
            ManageAppSettingsUseCase.SETTING_LSP_JDTLS_MAVEN_LOCAL_REPO,
            ManageAppSettingsUseCase.DEFAULT_LSP_JDTLS_MAVEN_LOCAL_REPO);
        if (launcherJar == null || launcherJar.isBlank()) {
            throw new IllegalStateException("JDTLS launcher JAR is not configured: "
                    + ManageAppSettingsUseCase.SETTING_LSP_JDTLS_LAUNCHER_JAR);
        }

        Path configPath = platformConfigurationResolver.resolveConfigPath();

        List<String> command = new ArrayList<>();
        command.add(javaCommand);
        command.add("-Dmaven.settings=" + mavenSettingsPath.toAbsolutePath().toString());
        command.add("-Dmaven.repo.local=" + localRepo);
        command.add("-jar");
        command.add(launcherJar);
        command.add("-configuration");
        command.add(configPath.toString());
        command.add("-data");
        command.add(workspacePath.toString());

        File stderrLog = workspacePath.resolve(STDERR_LOG_FILE_NAME).toFile();
        logger.info("JDTLS stderr will be written to {}", stderrLog.getAbsolutePath());

        var processBuilder = new ProcessBuilder(command);
        processBuilder.redirectError(stderrLog);
        processBuilder.directory(context.projectRoot().toFile());
        return processBuilder.start();
    }

    private static void initializeServer(LanguageServer languageServer, LspProjectContext context,
                                         Path mavenSettingsPath) {
        InitializeParams params = new InitializeParams();
        params.setRootUri(context.projectRootUri());
        params.setWorkspaceFolders(List.of(new WorkspaceFolder(
                context.projectRoot().toUri().toString(),
                context.repository().name().value())));

        ClientCapabilities capabilities = new ClientCapabilities();
        // workspace
        WorkspaceClientCapabilities workspaceCapabilities = new WorkspaceClientCapabilities();
        workspaceCapabilities.setConfiguration(true);
        workspaceCapabilities.setDidChangeConfiguration(new org.eclipse.lsp4j.DidChangeConfigurationCapabilities(true));
        capabilities.setWorkspace(workspaceCapabilities);
        // symbols im document -> text document
        DocumentSymbolCapabilities documentSymbolCapabilities = new DocumentSymbolCapabilities();
        documentSymbolCapabilities.setHierarchicalDocumentSymbolSupport(true);
        // hover -> text document
        HoverCapabilities hoverCapabilities = new HoverCapabilities();
        // 2. References – alle Verwendungsstellen eines Symbols finden
        //ReferenceCapabilities referenceCapabilities = new ReferenceCapabilities();
        //textDocumentCapabilities.setReferences(referenceCapabilities);
        // 3. Definition – zur Deklaration eines Symbols springen
        DefinitionCapabilities definitionCapabilities = new DefinitionCapabilities();
        // 4. Type Definition – zum Typ eines Ausdrucks springen
        TypeDefinitionCapabilities typeDefinitionCapabilities = new TypeDefinitionCapabilities();
        // 5. Implementation – Implementierungen eines Interfaces/abstrakte Methode finden
        ImplementationCapabilities implementationCapabilities = new ImplementationCapabilities();
        // 6. Call Hierarchy – Aufrufer und Aufgerufene einer Methode
        CallHierarchyCapabilities callHierarchyCapabilities = new CallHierarchyCapabilities();
        // 7. Semantic Tokens – semantische Klassifizierung aller Token im Dokument
        SemanticTokensCapabilities semanticTokensCapabilities = new SemanticTokensCapabilities();
        semanticTokensCapabilities.setRequests(new SemanticTokensClientCapabilitiesRequests(true, true));
        // text document
        TextDocumentClientCapabilities textDocumentCapabilities = new TextDocumentClientCapabilities();
        textDocumentCapabilities.setDocumentSymbol(documentSymbolCapabilities);
        textDocumentCapabilities.setHover(hoverCapabilities);
        textDocumentCapabilities.setDefinition(definitionCapabilities);
        textDocumentCapabilities.setTypeDefinition(typeDefinitionCapabilities);
        textDocumentCapabilities.setImplementation(implementationCapabilities);
        textDocumentCapabilities.setCallHierarchy(callHierarchyCapabilities);
        textDocumentCapabilities.setSemanticTokens(semanticTokensCapabilities);
        capabilities.setTextDocument(textDocumentCapabilities);
        // setzen
        params.setCapabilities(capabilities);

        params.setInitializationOptions(buildInitializationOptions(mavenSettingsPath));

        try {
            InitializeResult result = languageServer.initialize(params).get(30, TimeUnit.SECONDS);
            if (result == null) {
                throw new IllegalStateException("JDTLS initialize returned no result");
            }
            JdtlsVersionChecker.checkVersion(result);
            languageServer.initialized(new InitializedParams());

            // Push the same settings again after initialized() so that JDTLS re-reads them
            // once the server is fully up, covering any race between startup and first config read.
            languageServer.getWorkspaceService().didChangeConfiguration(
                    new DidChangeConfigurationParams(buildJavaSettings(mavenSettingsPath)));

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while initializing JDTLS language server", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to initialize JDTLS language server", exception);
        }
    }

    private static Map<String, Object> buildInitializationOptions(Path mavenSettingsPath) {
        return  Map.of("settings", Map.of("java", buildJavaSettings(mavenSettingsPath)));
    }

    /**
     * Builds the {@code java.*} settings map passed via both {@code initializationOptions}
     * and {@code workspace/didChangeConfiguration}.
     *
     * @param mavenSettingsPath absolute path to the generated {@code settings.xml}
     * @return nested settings map for the {@code "java"} section
     */
    private static Map<String, Object> buildJavaSettings(Path mavenSettingsPath) {
        return Map.of(
            "configuration", Map.of(
                "maven", Map.of(
                    "userSettings", mavenSettingsPath.toString(),
                    "updateSnapshots", true
                    //,"globalSettings", mavenSettingsPath     // optional, if there is a global settings
                )
            ),
            "import", Map.of(
                "maven", Map.of(
                    "enabled", true
                )
            )
        );
    }
}
