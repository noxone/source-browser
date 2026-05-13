package com.hlag.sourceviewer.application.scan.indexer.maven;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Repository;
import org.apache.maven.model.resolution.InvalidRepositoryException;
import org.apache.maven.model.resolution.ModelResolver;
import org.apache.maven.model.resolution.UnresolvableModelException;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.supplier.RepositorySystemSupplier;

class InMemoryModelResolver implements ModelResolver {
  private static final Logger logger = Logger.getLogger(InMemoryModelResolver.class.getName());
  private final RepositorySystem system = new RepositorySystemSupplier().get();
  private final DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
  private final List<RemoteRepository> repositories;

  public InMemoryModelResolver(MavenRepositoryProvider mavenRepositoryProvider) {
    session.setLocalRepositoryManager(
        system.newLocalRepositoryManager(session, mavenRepositoryProvider.buildLocalRepository()));

    repositories = mavenRepositoryProvider.buildRemoteRepositories();
  }

  @Override
  public InMemoryModelSource resolveModel(String groupId, String artifactId, String version)
      throws UnresolvableModelException {
    return resolveArtifact(new DefaultArtifact(groupId, artifactId, "pom", version));
  }

  @Override
  public InMemoryModelSource resolveModel(Parent parent) throws UnresolvableModelException {
    return resolveArtifact(
        new DefaultArtifact(
            parent.getGroupId(), parent.getArtifactId(), "pom", parent.getVersion()));
  }

  @Override
  public InMemoryModelSource resolveModel(Dependency dependency) throws UnresolvableModelException {
    return resolveArtifact(
        new DefaultArtifact(
            dependency.getGroupId(),
            dependency.getArtifactId(),
            dependency.getClassifier(),
            null,
            dependency.getVersion()));
  }

  @Override
  public void addRepository(Repository repository) throws InvalidRepositoryException {
    // left empty, because we don't care about repositories in this context
  }

  @Override
  public void addRepository(Repository repository, boolean replace)
      throws InvalidRepositoryException {
    // left empty, because we don't care about repositories in this context
  }

  @Override
  public ModelResolver newCopy() {
    return this;
  }

  @SuppressWarnings("java:S1162" /*using checked exception*/)
  public InMemoryModelSource resolveArtifact(Artifact artifact) throws UnresolvableModelException {
    if (repositories.isEmpty()) {
      throw new RuntimeException("No repository configured");
    }
    ArtifactRequest request = new ArtifactRequest();
    request.setArtifact(artifact);
    if (logger.isLoggable(Level.INFO)) {
      logger.info(
          String.format(
              "Resolving artifact %s:%s:%s using repositories %s",
              artifact.getGroupId(),
              artifact.getArtifactId(),
              artifact.getVersion(),
              repositories.stream()
                  .map(repo -> String.format("%s::%s", repo.getId(), repo.getUrl()))
                  .collect(Collectors.joining(","))));
    }
    request.setRepositories(repositories);

    ArtifactResult result;
    try {
      result = system.resolveArtifact(session, request);
    } catch (ArtifactResolutionException e) {
      throw new UnresolvableModelException(
          String.format(
              "Unable to resolve artifact: %s:%s:%s:%s",
              artifact.getGroupId(),
              artifact.getArtifactId(),
              artifact.getClassifier(),
              artifact.getVersion()),
          artifact.getGroupId(),
          artifact.getArtifactId(),
          artifact.getVersion(),
          e);
    }
    var file = result.getArtifact().getFile();
    try {
      byte[] content = java.nio.file.Files.readAllBytes(file.toPath());
      return new InMemoryModelSource(content);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read resolved POM file", e);
    }
  }
}
