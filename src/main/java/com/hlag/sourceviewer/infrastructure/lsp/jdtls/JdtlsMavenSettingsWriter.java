package com.hlag.sourceviewer.infrastructure.lsp.jdtls;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates a Maven {@code settings.xml} file for JDTLS that mirrors all Maven repository
 * traffic through the configured corporate repository.
 *
 * <p>JDTLS uses its own embedded Maven resolver and does not inherit the application's Maven
 * configuration. Passing a generated settings file via {@code java.configuration.maven.userSettings}
 * in the JDTLS initialization options ensures that all dependency and plugin resolution inside
 * JDTLS goes through the same Artifactory instance used by the rest of the application.</p>
 *
 * <p>The generated file is written to a stable path inside the JDTLS workspace directory so
 * that it persists across restarts and can be inspected for diagnostics.</p>
 */
@ApplicationScoped
public class JdtlsMavenSettingsWriter {

    private static final Logger logger = LoggerFactory.getLogger(JdtlsMavenSettingsWriter.class);

    static final String SETTINGS_FILE_NAME = "sourceviewer-maven-settings.xml";
    static final String SERVER_ID = "sourceviewer-mirror";

    /**
     * Writes a {@code settings.xml} to {@code workspacePath/sourceviewer-maven-settings.xml} and
     * returns the absolute path to the generated file.
     *
     * <p>If {@code repositoryUrl} is blank the method writes a minimal empty settings file so
     * that JDTLS always receives a syntactically valid document.</p>
     *
     * @param workspacePath  directory in which to write the file
     * @param repositoryUrl  URL of the Maven mirror (empty = no mirror)
     * @param username       mirror username (empty = no authentication)
     * @param password       mirror password (empty = no authentication)
     * @param localRepoPath  absolute path to a custom local repository (empty = Maven default)
     * @return absolute path to the written settings file
     * @throws IOException if the file cannot be created or written
     */
    public Path write(
            Path workspacePath,
            String repositoryUrl,
            String username,
            String password,
            String localRepoPath) throws IOException {

        Files.createDirectories(workspacePath);
        Path target = workspacePath.resolve(SETTINGS_FILE_NAME);

        try (Writer writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            writer.write(buildSettingsXml(repositoryUrl, username, password, localRepoPath));
        }

        logger.info("JDTLS Maven settings.xml written to {}", target.toAbsolutePath());
        return target.toAbsolutePath();
    }

    private static String buildSettingsXml(
        String repositoryUrl,
        String username,
        String password,
        String localRepoPath) {

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<settings xmlns=\"http://maven.apache.org/SETTINGS/1.0.0\"\n");
        xml.append("          xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        xml.append("          xsi:schemaLocation=\"http://maven.apache.org/SETTINGS/1.0.0 ");
        xml.append("http://maven.apache.org/xsd/settings-1.0.0.xsd\">\n\n");
        xml.append("  <!-- Generated automatically by sourceviewer. Do not edit manually. -->\n\n");

        if (!isBlank(localRepoPath)) {
            xml.append("  <localRepository>").append(escapeXml(localRepoPath)).append("</localRepository>\n\n");
        }

        boolean hasMirror = !isBlank(repositoryUrl);
        boolean hasAuth = hasMirror && !isBlank(username);

        if (hasAuth) {
            xml.append("  <servers>\n");
            xml.append("    <server>\n");
            xml.append("      <id>").append(SERVER_ID).append("</id>\n");
            xml.append("      <username>").append(escapeXml(username)).append("</username>\n");
            xml.append("      <password>").append(escapeXml(password)).append("</password>\n");
            xml.append("    </server>\n");
            xml.append("  </servers>\n\n");
        }

        if (hasMirror) {
            xml.append("  <mirrors>\n");
            xml.append("    <mirror>\n");
            xml.append("      <id>").append(SERVER_ID).append("</id>\n");
            xml.append("      <name>Sourceviewer Maven Repository Mirror</name>\n");
            xml.append("      <url>").append(escapeXml(repositoryUrl)).append("</url>\n");
            xml.append("      <mirrorOf>*</mirrorOf>\n");
            xml.append("    </mirror>\n");
            xml.append("  </mirrors>\n\n");
        }

        xml.append("</settings>\n");
        return xml.toString();
    }

    private static String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

