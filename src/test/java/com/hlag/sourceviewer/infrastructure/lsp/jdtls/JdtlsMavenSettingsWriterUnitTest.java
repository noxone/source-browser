package com.hlag.sourceviewer.infrastructure.lsp.jdtls;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdtlsMavenSettingsWriterUnitTest {

    @TempDir
    Path tempDir;

    private JdtlsMavenSettingsWriter writer;

    @BeforeEach
    void setUp() {
        writer = new JdtlsMavenSettingsWriter();
    }

    @Test
    void written_file_is_named_correctly() throws IOException {
        Path result = write("https://repo.example.com/", "user", "pass", "");

        assertThat(result.getFileName().toString())
                .isEqualTo(JdtlsMavenSettingsWriter.SETTINGS_FILE_NAME);
    }

    @Test
    void returned_path_is_absolute_and_exists() throws IOException {
        Path result = write("https://repo.example.com/", "user", "pass", "");

        assertThat(result).isAbsolute();
        assertThat(Files.exists(result)).isTrue();
    }

    @Test
    void generated_xml_contains_mirror_url_and_server_id() throws IOException {
        Path result = write("https://artifactory.corp.example.com/virtrepo/", "alice", "secret", "");
        String xml = readXml(result);

        assertThat(xml).contains("https://artifactory.corp.example.com/virtrepo/");
        assertThat(xml).contains(JdtlsMavenSettingsWriter.SERVER_ID);
        assertThat(xml).contains("<mirrorOf>*</mirrorOf>");
    }

    @Test
    void generated_xml_contains_server_credentials() throws IOException {
        Path result = write("https://repo.example.com/", "alice", "s3cr3t", "");
        String xml = readXml(result);

        assertThat(xml).contains("<username>alice</username>");
        assertThat(xml).contains("<password>s3cr3t</password>");
    }

    @Test
    void generated_xml_contains_local_repository_when_configured() throws IOException {
        Path result = write("https://repo.example.com/", "user", "pass", "/opt/maven/repo");
        String xml = readXml(result);

        assertThat(xml).contains("<localRepository>/opt/maven/repo</localRepository>");
    }

    @Test
    void generated_xml_omits_local_repository_element_when_empty() throws IOException {
        Path result = write("https://repo.example.com/", "user", "pass", "");
        String xml = readXml(result);

        assertThat(xml).doesNotContain("<localRepository>");
    }

    @Test
    void generated_xml_omits_server_block_when_no_username() throws IOException {
        Path result = write("https://repo.example.com/", "", "", "");
        String xml = readXml(result);

        assertThat(xml).doesNotContain("<servers>");
        assertThat(xml).doesNotContain("<username>");
    }

    @Test
    void generated_xml_omits_mirror_block_when_url_is_blank() throws IOException {
        Path result = write("", "", "", "");
        String xml = readXml(result);

        assertThat(xml).doesNotContain("<mirrors>");
        assertThat(xml).doesNotContain("<servers>");
    }

    @Test
    void special_xml_characters_in_password_are_escaped() throws IOException {
        Path result = write("https://repo.example.com/", "user", "p&ss<w>ord\"'", "");
        String xml = readXml(result);

        assertThat(xml).contains("p&amp;ss&lt;w&gt;ord&quot;&apos;");
        assertThat(xml).doesNotContain("p&ss");
    }

    @Test
    void generated_xml_is_valid_xml_declaration() throws IOException {
        Path result = write("https://repo.example.com/", "user", "pass", "");
        String xml = readXml(result);

        assertThat(xml).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
    }

    @Test
    void workspace_directory_is_created_if_absent() throws IOException {
        Path subDirectory = tempDir.resolve("new/nested/workspace");

        Path result = writer.write(subDirectory, "https://repo.example.com/", "", "", "");

        assertThat(Files.isDirectory(subDirectory)).isTrue();
        assertThat(Files.exists(result)).isTrue();
    }

    private Path write(String url, String username, String password, String localRepo) throws IOException {
        return writer.write(tempDir, url, username, password, localRepo);
    }

    private static String readXml(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
