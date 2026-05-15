package com.hlag.sourceviewer.application.scan.indexer;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.hlag.sourceviewer.application.scan.JavaFileParser;
import com.hlag.sourceviewer.application.scan.ParsedFile;
import com.hlag.sourceviewer.application.scan.indexer.maven.MavenRepositoryProvider;
import com.hlag.sourceviewer.application.scan.indexer.maven.PomFileLoader;
import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maven-aware Java indexer. Activates when ≥60% of Java source files are covered by
 * Maven projects discovered anywhere in the repository tree. When active, resolves
 * transitive compile/runtime dependencies for each Maven project and adds the resulting
 * JARs to the TypeSolver for accurate symbol resolution of library types.
 */
@ApplicationScoped
public class MavenAwareJavaIndexer implements LanguageIndexer {

    private static final Logger logger = LoggerFactory.getLogger(MavenAwareJavaIndexer.class);

    static final double MAVEN_COVERAGE_THRESHOLD = 0.60;
    private static final Set<String> SKIP_DIRS =
            Set.of(".git", "target", "build", "node_modules", ".gradle", ".idea");

    private final JavaFileParser javaFileParser;
    private final MavenRepositoryProvider mavenRepositoryProvider;
    private final PomFileLoader pomFileLoader;

    @Inject
    public MavenAwareJavaIndexer(JavaFileParser javaFileParser, MavenRepositoryProvider mavenRepositoryProvider, PomFileLoader pomFileLoader) {
        this.javaFileParser = javaFileParser;
        this.mavenRepositoryProvider = mavenRepositoryProvider;
        this.pomFileLoader = pomFileLoader;
    }

    @Override
    public String supportedLanguage() {
        return "java";
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public boolean analyze(Path repoRoot, List<FilePath> allFiles) {
        List<Path> pomDirs = findPomDirectories(repoRoot);
        if (pomDirs.isEmpty()) {
            return false;
        }
        List<FilePath> javaFiles = allFiles.stream().filter(FilePath::isJavaFile).toList();
        if (javaFiles.isEmpty()) {
            return false;
        }
        long covered = javaFiles.stream()
                .filter(f -> pomDirs.stream().anyMatch(dir -> isCoveredByMaven(repoRoot, f, dir)))
                .count();
        double ratio = (double) covered / javaFiles.size();
        logger.debug("Maven coverage for repo {}: {}/{} Java files ({:.0f}%)",
                repoRoot, covered, javaFiles.size(), ratio * 100);
        return ratio >= MAVEN_COVERAGE_THRESHOLD;
    }

    @Override
    public Object prepare(Path repoRoot) {
        var solver = javaFileParser.buildCombinedTypeSolver(repoRoot);
        for (Path pomDir : findPomDirectories(repoRoot)) {
            List<Path> jars = resolveMavenDependencies(pomDir);
            for (Path jar : jars) {
                try {
                    solver.add(new JarTypeSolver(jar));
                } catch (IOException e) {
                    logger.warn("Cannot add JAR to TypeSolver: {}", jar, e);
                }
            }
        }
        return new JavaIndexingContext(solver);
    }

    @Override
    public boolean handles(FilePath path) {
        return path.isJavaFile();
    }

    @Override
    public ParsedFile indexFile(FileIdentifier fileId, FilePath path,
                                               String content, Object context) {
        return javaFileParser.parse(fileId, path, content, ((JavaIndexingContext) context).typeSolver());
    }

    // ── Maven project discovery ───────────────────────────────────────────────

    List<Path> findPomDirectories(Path repoRoot) {
        var dirs = new ArrayList<Path>();
        try {
            Files.walkFileTree(repoRoot, Set.of(), 20, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String name = dir.getFileName() != null ? dir.getFileName().toString() : "";
                    return SKIP_DIRS.contains(name) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if ("pom.xml".equals(file.getFileName().toString())) {
                        dirs.add(file.getParent());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            logger.warn("Could not walk repo tree for pom.xml under {}: {}", repoRoot, e.getMessage());
        }
        return dirs;
    }

    boolean isCoveredByMaven(Path repoRoot, FilePath file, Path pomDir) {
        Path relPomDir = repoRoot.relativize(pomDir);
        String prefix = relPomDir.toString().replace('\\', '/');
        String filePath = file.value().replace('\\', '/');
        String pomPrefix = prefix.isEmpty() ? "" : prefix + "/";
        return filePath.startsWith(pomPrefix + "src/main/java/")
                || filePath.startsWith(pomPrefix + "src/test/java/");
    }

    // ── Maven dependency resolution ───────────────────────────────────────────

    private List<Path> resolveMavenDependencies(Path pomDir) {
        Path pomFile = pomDir.resolve("pom.xml");
        if (!Files.exists(pomFile)) {
            return List.of();
        }
        try {
            logger.debug("Inspecting POM {}", pomDir);

            RepositorySystem system = new RepositorySystemSupplier().get();
            DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
            session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, mavenRepositoryProvider.buildLocalRepository()));
            session.setReadOnly();
            List<RemoteRepository> remotes = mavenRepositoryProvider.buildRemoteRepositories();

            var effectiveModel = pomFileLoader.loadEffectiveModel(Files.readString(pomFile, UTF_8),
                // TODO: This should be configurable (per repository)
                Map.of(
                    "revision", "1.0.0",
                    "changelist", "-SNAPSHOT"));
            var dependencies = effectiveModel.getDependencies();
            var dependencyManagement = Optional.ofNullable(effectiveModel.getDependencyManagement())
                .map(DependencyManagement::getDependencies)
                .orElseGet(List::of)
                .stream()
                .collect(Collectors.toMap(
                    dependency -> new ShortArtifact(dependency.getGroupId(), dependency.getArtifactId()),
                    Dependency::getVersion,
                    (a, b) -> a));

            var loadedArtifacts = dependencies.stream()
                .map(dependency -> {
                    if (dependency.getVersion() == null || dependency.getVersion().isBlank()) {
                        var art = new ShortArtifact(dependency.getGroupId(), dependency.getArtifactId());
                        if (dependencyManagement.containsKey(art)) {
                            dependency.setVersion(dependencyManagement.get(art));
                        }
                    }
                    return dependency;
                })
                .filter(dependency -> dependency.getVersion() != null && !dependency.getVersion().isBlank())
                .map(dependency -> resolveWithTransitives(dependency, system, session, remotes)).flatMap(Collection::stream).collect(Collectors.toSet());

            List<Path> jars = new ArrayList<>();
            for (var artifactResult : loadedArtifacts) {
                if (artifactResult.isResolved()) {
                    java.io.File jarFile = artifactResult.getArtifact().getFile();
                    if (jarFile != null && jarFile.getName().endsWith(".jar")) {
                        jars.add(jarFile.toPath());
                    }
                }
            }
            logger.debug("Resolved {} JARs for Maven project at {}", jars.size(), pomDir);
            return jars;
        } catch (Exception e) {
            logger.warn("Failed to resolve Maven dependencies for POM {}: {}", pomDir, e.getMessage());
            return List.of();
        }
    }

    private record ShortArtifact(String groupId, String artifactId){}

    private Collection<ArtifactResult> resolveWithTransitives(Dependency dependency,RepositorySystem system,DefaultRepositorySystemSession session, List<RemoteRepository> remoteRepositories)
         {
        var aetherDependency =
            new org.eclipse.aether.graph.Dependency(toArtifact(dependency), dependency.getScope())
                .setExclusions(
                    dependency.getExclusions().stream()
                        .map(excl -> new Exclusion(excl.getGroupId(), excl.getArtifactId(), "", ""))
                        .toList());
         logger.debug("Resolving artifact with transitives: {}", aetherDependency);

        var request = new CollectRequest();
        request.setRoot(aetherDependency);
        request.setRepositories(remoteRepositories);

        var dependencyRequest = new DependencyRequest(request, null);
           DependencyResult dependencyResult = null;
           try {
             dependencyResult = system.resolveDependencies(session, dependencyRequest);
           } catch (DependencyResolutionException e) {
               logger.warn("Failed to resolve Maven dependencies for dependency {}: {}", dependency, e.getMessage());
             return Collections.emptySet();
           }

           return dependencyResult.getArtifactResults();
    }


    private static Artifact toArtifact(Dependency dependency) {
        return new DefaultArtifact(
            dependency.getGroupId(),
            dependency.getArtifactId(),
            dependency.getClassifier(),
            dependency.getType(),
            dependency.getVersion());
    }
}
