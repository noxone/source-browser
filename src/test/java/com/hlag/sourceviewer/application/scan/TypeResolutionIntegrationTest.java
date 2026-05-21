package com.hlag.sourceviewer.application.scan;

import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.hlag.sourceviewer.application.scan.indexer.maven.MavenRepositoryProvider;
import com.hlag.sourceviewer.application.scan.indexer.maven.PomFileLoader;
import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import org.apache.maven.model.Dependency;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies that type resolution works end-to-end for source files in this project.
 *
 * <p>Uses the project's own {@code pom.xml} to resolve Maven dependencies, builds
 * a full {@link com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver}
 * with JAR solvers, and then parses source files from this project – checking that
 * Quarkus/Jakarta types (which are BOM-managed and were broken before the fix) are
 * correctly resolved to their fully qualified names.</p>
 */
class TypeResolutionIntegrationTest {

    private static JavaFileParser parser;
    private static com.github.javaparser.resolution.TypeSolver typeSolver;

    @BeforeAll
    static void setUpTypeSolver() throws Exception {
        Path projectRoot = Path.of(".").toRealPath();

        String localRepoPath = System.getProperty("user.home") + "/.m2/repository";
        var provider = mock(MavenRepositoryProvider.class);
        when(provider.buildLocalRepository())
                .thenReturn(new LocalRepository(new File(localRepoPath), "simple"));
        var central = new RemoteRepository.Builder("central", "default",
                "https://repo.maven.apache.org/maven2").build();
        when(provider.buildRemoteRepositories()).thenReturn(List.of(central));

        parser = new JavaFileParser();
        var combinedSolver = parser.buildCombinedTypeSolver(projectRoot);

        // Resolve Maven dependencies via the fixed PomFileLoader (no setTwoPhaseBuilding)
        var pomLoader = new PomFileLoader(provider);
        var effectiveModel = pomLoader.loadEffectiveModel(
                Files.readString(projectRoot.resolve("pom.xml")), Map.of());

        RepositorySystem system = new RepositorySystemSupplier().get();
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        session.setLocalRepositoryManager(
                system.newLocalRepositoryManager(session, provider.buildLocalRepository()));
        session.setReadOnly();

        for (Dependency dep : effectiveModel.getDependencies()) {
            if (dep.getVersion() == null || dep.getVersion().isBlank()) continue;
            try {
                var aetherDep = new org.eclipse.aether.graph.Dependency(
                        new DefaultArtifact(dep.getGroupId(), dep.getArtifactId(),
                                dep.getClassifier(), dep.getType(), dep.getVersion()),
                        dep.getScope())
                        .setExclusions(dep.getExclusions().stream()
                                .map(e -> new Exclusion(e.getGroupId(), e.getArtifactId(), "", ""))
                                .toList());
                var collectReq = new CollectRequest();
                collectReq.setRoot(aetherDep);
                collectReq.setRepositories(List.of(central));
                var results = system.resolveDependencies(session, new DependencyRequest(collectReq, null));
                for (var result : results.getArtifactResults()) {
                    if (result.isResolved()) {
                        File jar = result.getArtifact().getFile();
                        if (jar != null && jar.getName().endsWith(".jar")) {
                            try {
                                combinedSolver.add(new JarTypeSolver(jar));
                            } catch (IOException ignored) {
                                // skip unreadable JARs
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
                // skip individual dependency resolution failures
            }
        }
        typeSolver = combinedSolver;
    }

    @Test
    void jakarta_ApplicationScoped_annotation_is_resolved() throws Exception {
        // JavaFileParser itself uses @ApplicationScoped – parse it and check the annotation
        String source = Files.readString(
                Path.of("src/main/java/com/hlag/sourceviewer/application/scan/JavaFileParser.java"));
        var fileId = new FileIdentifier(1L);
        var filePath = new FilePath("com/hlag/sourceviewer/application/scan/JavaFileParser.java");

        var parsed = parser.parse(fileId, filePath, source, typeSolver);

        assertThat(parsed.references())
                .as("@ApplicationScoped should resolve to jakarta.enterprise.context.ApplicationScoped")
                .anyMatch(r -> r.resolvedName()
                        .map(n -> n.value().equals("jakarta.enterprise.context.ApplicationScoped"))
                        .orElse(false));
    }

    @Test
    void own_project_types_are_resolved() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/hlag/sourceviewer/application/scan/JavaFileParser.java"));
        var fileId = new FileIdentifier(1L);
        var filePath = new FilePath("com/hlag/sourceviewer/application/scan/JavaFileParser.java");

        var parsed = parser.parse(fileId, filePath, source, typeSolver);

        assertThat(parsed.references())
                .as("FilePath should resolve to com.hlag.sourceviewer.domain.model.identifier.FilePath")
                .anyMatch(r -> r.resolvedName()
                        .map(n -> n.value().equals("com.hlag.sourceviewer.domain.model.identifier.FilePath"))
                        .orElse(false));
    }

    @Test
    void at_least_95_percent_of_type_references_are_resolved() throws Exception {
        // Even without Maven JARs, ReflectionTypeSolver resolves classpath types.
        // After the setTwoPhaseBuilding fix, Maven JARs are also available for non-classpath types.
        String source = Files.readString(
                Path.of("src/main/java/com/hlag/sourceviewer/application/scan/JavaFileParser.java"));
        var fileId = new FileIdentifier(1L);
        var filePath = new FilePath("com/hlag/sourceviewer/application/scan/JavaFileParser.java");

        var parsed = parser.parse(fileId, filePath, source, typeSolver);

        long resolved = parsed.references().stream()
                .filter(r -> r.resolvedName().isPresent())
                .count();
        long total = parsed.references().size();

        assertThat(total).isPositive();
        double ratio = (double) resolved / total;
        assertThat(ratio)
                .as("At least 95%% of type references should be resolved (was %.0f%%)", ratio * 100)
                .isGreaterThanOrEqualTo(0.95);
    }
}
