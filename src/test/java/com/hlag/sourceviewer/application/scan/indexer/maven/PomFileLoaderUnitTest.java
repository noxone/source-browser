package com.hlag.sourceviewer.application.scan.indexer.maven;

import org.apache.maven.model.Model;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link PomFileLoader} builds fully effective Maven models,
 * including BOM import resolution.  The most important regression to catch
 * is the (now removed) {@code setTwoPhaseBuilding(true)} setting that caused
 * the second build phase – responsible for expanding {@code <dependencyManagement>}
 * BOM imports and injecting managed versions into {@code <dependencies>} – to be
 * skipped entirely, leaving most BOM-managed dependency versions as {@code null}.
 */
class PomFileLoaderUnitTest {

    private PomFileLoader loader;

    @BeforeEach
    void setUp() {
        String localRepoPath = System.getProperty("user.home") + "/.m2/repository";
        var provider = mock(MavenRepositoryProvider.class);
        when(provider.buildLocalRepository())
                .thenReturn(new LocalRepository(new java.io.File(localRepoPath), "simple"));
        when(provider.buildRemoteRepositories())
                .thenReturn(List.of(new RemoteRepository.Builder("central", "default",
                        "https://repo.maven.apache.org/maven2").build()));
        loader = new PomFileLoader(provider);
    }

    @Test
    void bom_managed_dependency_versions_are_resolved() throws Exception {
        String pomContent = Files.readString(Path.of("pom.xml"));

        Model effective = loader.loadEffectiveModel(pomContent, java.util.Map.of());

        // io.quarkus:quarkus-rest has no explicit version in source-browser's pom.xml –
        // its version is managed by the Quarkus BOM.  Before the fix (setTwoPhaseBuilding=true),
        // the BOM import was never expanded so this version was always null.
        var quarkusRest = effective.getDependencies().stream()
                .filter(d -> "io.quarkus".equals(d.getGroupId())
                        && "quarkus-rest".equals(d.getArtifactId()))
                .findFirst();

        assertThat(quarkusRest)
                .as("io.quarkus:quarkus-rest must be present in the effective POM")
                .isPresent();
        assertThat(quarkusRest.get().getVersion())
                .as("io.quarkus:quarkus-rest version must be resolved from the Quarkus BOM")
                .isNotBlank();
    }

    @Test
    void explicitly_versioned_dependency_is_resolved() throws Exception {
        String pomContent = Files.readString(Path.of("pom.xml"));

        Model effective = loader.loadEffectiveModel(pomContent, java.util.Map.of());

        var javaparser = effective.getDependencies().stream()
                .filter(d -> "com.github.javaparser".equals(d.getGroupId())
                        && "javaparser-symbol-solver-core".equals(d.getArtifactId()))
                .findFirst();

        assertThat(javaparser)
                .as("javaparser-symbol-solver-core must be present in the effective POM")
                .isPresent();
        assertThat(javaparser.get().getVersion())
                .as("javaparser version must not be blank")
                .isNotBlank();
    }
}
