package com.hlag.sourceviewer.application.scan.indexer;

import com.hlag.sourceviewer.application.scan.JavaFileParser;
import com.hlag.sourceviewer.application.scan.indexer.maven.MavenRepositoryProvider;
import com.hlag.sourceviewer.application.scan.indexer.maven.PomFileLoader;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MavenAwareJavaIndexerUnitTest {

    @TempDir
    Path repoRoot;

    private MavenAwareJavaIndexer indexer;

    @BeforeEach
    void setUp() {
        indexer = new MavenAwareJavaIndexer(
                mock(JavaFileParser.class),
                mock(MavenRepositoryProvider.class),
                mock(PomFileLoader.class));
    }

    @Test
    void priority_is_lower_than_generic_indexer() {
        var generic = new GenericJavaIndexer(mock(JavaFileParser.class));
        assertThat(indexer.priority()).isLessThan(generic.priority());
    }

    @Test
    void supported_language_is_java() {
        assertThat(indexer.supportedLanguage()).isEqualTo("java");
    }

    @Test
    void analyze_returns_false_when_no_pom_xml_found() {
        List<FilePath> files = mavenJavaFiles("", 10);
        assertThat(indexer.analyze(repoRoot, files)).isFalse();
    }

    @Test
    void analyze_returns_false_when_pom_exists_but_coverage_below_threshold() throws IOException {
        Files.createFile(repoRoot.resolve("pom.xml"));
        // 2 out of 10 Java files are in Maven layout — 20%, below 60%
        List<FilePath> files = List.of(
                new FilePath("src/main/java/Foo.java"),
                new FilePath("src/main/java/Bar.java"),
                new FilePath("scripts/A.java"),
                new FilePath("scripts/B.java"),
                new FilePath("scripts/C.java"),
                new FilePath("scripts/D.java"),
                new FilePath("scripts/E.java"),
                new FilePath("scripts/F.java"),
                new FilePath("scripts/G.java"),
                new FilePath("scripts/H.java")
        );
        assertThat(indexer.analyze(repoRoot, files)).isFalse();
    }

    @Test
    void analyze_returns_true_when_pom_exists_and_majority_in_maven_layout() throws IOException {
        Files.createFile(repoRoot.resolve("pom.xml"));
        // 8 out of 10 Java files are in Maven layout — 80%, above 60%
        List<FilePath> files = List.of(
                new FilePath("src/main/java/Foo.java"),
                new FilePath("src/main/java/Bar.java"),
                new FilePath("src/main/java/Baz.java"),
                new FilePath("src/test/java/FooTest.java"),
                new FilePath("src/test/java/BarTest.java"),
                new FilePath("src/test/java/BazTest.java"),
                new FilePath("src/main/java/Service.java"),
                new FilePath("src/main/java/Repo.java"),
                new FilePath("scripts/Tool.java"),
                new FilePath("misc/Util.java")
        );
        assertThat(indexer.analyze(repoRoot, files)).isTrue();
    }

    @Test
    void analyze_handles_multi_module_repo_with_nested_poms() throws IOException {
        // Module A has a pom.xml and covers its Java files
        Path moduleA = repoRoot.resolve("moduleA");
        Files.createDirectories(moduleA);
        Files.createFile(moduleA.resolve("pom.xml"));

        List<FilePath> files = List.of(
                new FilePath("moduleA/src/main/java/AService.java"),
                new FilePath("moduleA/src/main/java/ARepo.java"),
                new FilePath("moduleA/src/test/java/AServiceTest.java"),
                new FilePath("moduleA/src/test/java/ARepoTest.java"),
                new FilePath("moduleA/src/main/java/AController.java"),
                new FilePath("moduleA/src/main/java/AModel.java"),
                new FilePath("moduleA/src/main/java/AMapper.java"),
                new FilePath("moduleA/src/main/java/AConfig.java"),
                new FilePath("scripts/Build.java"),
                new FilePath("scripts/Deploy.java")
        );
        // 8/10 = 80% covered
        assertThat(indexer.analyze(repoRoot, files)).isTrue();
    }

    @Test
    void analyze_returns_false_when_no_java_files() throws IOException {
        Files.createFile(repoRoot.resolve("pom.xml"));
        List<FilePath> files = List.of(new FilePath("README.md"), new FilePath("pom.xml"));
        assertThat(indexer.analyze(repoRoot, files)).isFalse();
    }

    @Test
    void findPomDirectories_discovers_pom_at_root() throws IOException {
        Files.createFile(repoRoot.resolve("pom.xml"));
        assertThat(indexer.findPomDirectories(repoRoot)).containsExactly(repoRoot);
    }

    @Test
    void findPomDirectories_discovers_poms_in_subdirectories() throws IOException {
        Path sub = repoRoot.resolve("module");
        Files.createDirectories(sub);
        Files.createFile(repoRoot.resolve("pom.xml"));
        Files.createFile(sub.resolve("pom.xml"));

        assertThat(indexer.findPomDirectories(repoRoot)).containsExactlyInAnyOrder(repoRoot, sub);
    }

    @Test
    void findPomDirectories_skips_target_directory() throws IOException {
        Path target = repoRoot.resolve("target");
        Files.createDirectories(target);
        Files.createFile(repoRoot.resolve("pom.xml"));
        Files.createFile(target.resolve("pom.xml"));

        assertThat(indexer.findPomDirectories(repoRoot)).containsExactly(repoRoot);
    }

    @Test
    void isCoveredByMaven_true_for_src_main_java_file() {
        FilePath file = new FilePath("src/main/java/com/example/Foo.java");
        assertThat(indexer.isCoveredByMaven(repoRoot, file, repoRoot)).isTrue();
    }

    @Test
    void isCoveredByMaven_true_for_src_test_java_file() {
        FilePath file = new FilePath("src/test/java/com/example/FooTest.java");
        assertThat(indexer.isCoveredByMaven(repoRoot, file, repoRoot)).isTrue();
    }

    @Test
    void isCoveredByMaven_false_for_file_outside_maven_layout() {
        FilePath file = new FilePath("scripts/Tool.java");
        assertThat(indexer.isCoveredByMaven(repoRoot, file, repoRoot)).isFalse();
    }

    @Test
    void isCoveredByMaven_true_for_submodule_file() throws IOException {
        Path moduleDir = repoRoot.resolve("module");
        Files.createDirectories(moduleDir);
        FilePath file = new FilePath("module/src/main/java/Foo.java");
        assertThat(indexer.isCoveredByMaven(repoRoot, file, moduleDir)).isTrue();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private List<FilePath> mavenJavaFiles(String prefix, int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new FilePath(prefix + "src/main/java/Class" + i + ".java"))
                .collect(Collectors.toList());
    }
}
