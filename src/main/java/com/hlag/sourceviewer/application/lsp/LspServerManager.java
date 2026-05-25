package com.hlag.sourceviewer.application.lsp;

import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages a pool of running {@link LspServerProcess} instances, one per
 * (language, workspace) combination.
 *
 * <p>LSP servers are started lazily on first use and reused across both scan-time
 * and interactive hover queries. Idle servers are shut down automatically after
 * the configured inactivity timeout.</p>
 */
@ApplicationScoped
public class LspServerManager {

    private static final Logger logger = LoggerFactory.getLogger(LspServerManager.class);

    private final String jdtlsLauncherJar;
    private final String jdtlsConfigDir;
    private final String jdtlsDataDir;
    private final String typescriptCommand;
    private final int inactiveTimeoutMinutes;

    private final Map<LspKey, LspServerProcess> servers = new ConcurrentHashMap<>();

    @Inject
    public LspServerManager(
            @ConfigProperty(name = "sourceviewer.lsp.jdtls.launcher-jar",
                    defaultValue = "/opt/jdtls/plugins/org.eclipse.equinox.launcher.jar")
            String jdtlsLauncherJar,
            @ConfigProperty(name = "sourceviewer.lsp.jdtls.config-dir",
                    defaultValue = "/opt/jdtls/config_linux")
            String jdtlsConfigDir,
            @ConfigProperty(name = "sourceviewer.lsp.jdtls.data-dir",
                    defaultValue = "/tmp/sourceviewer-jdtls-workspace")
            String jdtlsDataDir,
            @ConfigProperty(name = "sourceviewer.lsp.typescript.command",
                    defaultValue = "typescript-language-server")
            String typescriptCommand,
            @ConfigProperty(name = "sourceviewer.lsp.inactive-timeout-minutes",
                    defaultValue = "60")
            int inactiveTimeoutMinutes) {
        this.jdtlsLauncherJar = jdtlsLauncherJar;
        this.jdtlsConfigDir = jdtlsConfigDir;
        this.jdtlsDataDir = jdtlsDataDir;
        this.typescriptCommand = typescriptCommand;
        this.inactiveTimeoutMinutes = inactiveTimeoutMinutes;
    }

    /**
     * Returns an existing, alive LSP server for the given language and workspace,
     * or starts a new one. Returns empty if the server cannot be started.
     */
    public Optional<LspServerProcess> getOrCreate(String language, Path workspacePath) {
        LspKey key = new LspKey(language, workspacePath);
        LspServerProcess existing = servers.get(key);
        if (existing != null && existing.isAlive()) {
            return Optional.of(existing);
        }
        if (existing != null) {
            servers.remove(key, existing);
            existing.close();
        }
        return startNew(language, workspacePath).map(proc -> {
            servers.put(key, proc);
            return proc;
        });
    }

    /** Shuts down all managed LSP server processes. Called on application shutdown. */
    @PreDestroy
    void shutdownAll() {
        logger.info("Shutting down {} LSP server(s)", servers.size());
        servers.values().forEach(LspServerProcess::close);
        servers.clear();
    }

    /** Periodically removes servers that have been idle for more than the configured timeout. */
    @Scheduled(every = "10m")
    void pruneInactive() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(inactiveTimeoutMinutes));
        servers.entrySet().removeIf(entry -> {
            LspServerProcess proc = entry.getValue();
            if (!proc.isAlive() || proc.lastAccessed().isBefore(cutoff)) {
                logger.info("Pruning idle LSP server for language={} workspace={}",
                        proc.language(), proc.workspacePath());
                proc.close();
                return true;
            }
            return false;
        });
    }

    private Optional<LspServerProcess> startNew(String language, Path workspacePath) {
        try {
            List<String> command = buildCommand(language, workspacePath);
            if (command.isEmpty()) {
                logger.warn("No LSP command configured for language={}", language);
                return Optional.empty();
            }
            LspServerProcess proc = LspServerProcess.start(command, language, workspacePath);
            return Optional.of(proc);
        } catch (IOException e) {
            logger.error("Failed to start LSP server for language={} workspace={}: {}",
                    language, workspacePath, e.getMessage());
            return Optional.empty();
        }
    }

    private List<String> buildCommand(String language, Path workspacePath) throws IOException {
        return switch (language) {
            case "java" -> buildJdtlsCommand(workspacePath);
            case "typescript" -> List.of(typescriptCommand, "--stdio");
            default -> List.of();
        };
    }

    private List<String> buildJdtlsCommand(Path workspacePath) throws IOException {
        String launcherJar = resolveJdtlsLauncherJar();
        Path dataDir = Paths.get(jdtlsDataDir).resolve(
                workspacePath.getFileName().toString() + "-" + Math.abs(workspacePath.hashCode()));
        Files.createDirectories(dataDir);

        List<String> cmd = new ArrayList<>();
        cmd.add("java");
        cmd.add("-Declipse.application=org.eclipse.jdt.ls.core.id1");
        cmd.add("-Dosgi.bundles.defaultStartLevel=4");
        cmd.add("-Declipse.product=org.eclipse.jdt.ls.core.product");
        cmd.add("-Dlog.level=ERROR");
        cmd.add("-Xmx1G");
        cmd.add("--add-modules=ALL-SYSTEM");
        cmd.add("--add-opens=java.base/java.util=ALL-UNNAMED");
        cmd.add("--add-opens=java.base/java.lang=ALL-UNNAMED");
        cmd.add("-jar");
        cmd.add(launcherJar);
        cmd.add("-configuration");
        cmd.add(jdtlsConfigDir);
        cmd.add("-data");
        cmd.add(dataDir.toString());
        return cmd;
    }

    /** Finds the actual launcher JAR in the jdtls plugins directory (wildcard resolution). */
    private String resolveJdtlsLauncherJar() throws IOException {
        Path configured = Paths.get(jdtlsLauncherJar);
        if (Files.isRegularFile(configured)) {
            return jdtlsLauncherJar;
        }
        Path pluginsDir = configured.getParent();
        if (pluginsDir != null && Files.isDirectory(pluginsDir)) {
            try (var stream = Files.list(pluginsDir)) {
                return stream
                        .filter(p -> p.getFileName().toString().startsWith("org.eclipse.equinox.launcher_"))
                        .filter(p -> p.getFileName().toString().endsWith(".jar"))
                        .map(Path::toString)
                        .findFirst()
                        .orElse(jdtlsLauncherJar);
            }
        }
        return jdtlsLauncherJar;
    }

    private record LspKey(String language, Path workspacePath) {
        @Override public boolean equals(Object o) {
            if (!(o instanceof LspKey k)) return false;
            return Objects.equals(language, k.language) && Objects.equals(workspacePath, k.workspacePath);
        }
        @Override public int hashCode() { return Objects.hash(language, workspacePath); }
    }
}
