package com.hlag.sourceviewer.application.scan.indexer.maven;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingResult;
import org.apache.maven.model.building.ModelSource2;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.model.resolution.UnresolvableModelException;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.aether.artifact.DefaultArtifact;

@Dependent
public class PomFileLoader {
  private static final Logger logger = Logger.getLogger(PomFileLoader.class.getName());

  private static final MavenXpp3Reader mavenModelReader = new MavenXpp3Reader();
  private static final MavenXpp3Writer mavenModelWriter = new MavenXpp3Writer();

  private final InMemoryModelResolver modelResolver;

  public static Model loadRawModel(String pomContent) {
    try {
      return mavenModelReader.read(new StringReader(pomContent));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (XmlPullParserException e) {
      throw new PomException("Failed to parse POM content", e);
    }
  }

  @Inject
  public PomFileLoader(MavenRepositoryProvider mavenRepositoryProvider) {
    this.modelResolver = new InMemoryModelResolver(mavenRepositoryProvider);
  }

  public Optional<Model> loadEffectiveModel(
      String groupId, String artifactId, String extension, String version) {
    try {
      var model = actuallyLoadEffectiveModel(groupId, artifactId, extension, version);
      return Optional.of(model);
    } catch (UnresolvableModelException e) {
      logger.log(
          Level.WARNING,
          () ->
              String.format(
                  "Unable to load effective model for %s:%s:%s:%s -> %s",
                  groupId, artifactId, extension, version, e.getMessage()));
      return Optional.empty();
    }
  }

  private Model actuallyLoadEffectiveModel(
      String groupId, String artifactId, String extension, String version)
      throws UnresolvableModelException {
    var modelSource =
        modelResolver.resolveArtifact(new DefaultArtifact(groupId, artifactId, extension, version));
    return loadEffectiveModel(modelSource, new Properties());
  }

  public Model loadEffectiveModel(Model model) {
    final StringWriter writer = new StringWriter();
    try {
      mavenModelWriter.write(writer, model);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return loadEffectiveModel(writer.toString(), Map.of());
  }

  public Model loadEffectiveModel(String pomContent, Map<String, String> properties) {
    // TODO This replacement should not be necessary
    var replacedPomContent = pomContent;
    var userProperties = new Properties();
    for (var entry : properties.entrySet()) {
      replacedPomContent = replacedPomContent.replace("${"+entry.getKey()+"}", entry.getValue());
      userProperties.put(entry.getKey(), entry.getValue());
    }

    return loadEffectiveModel(new InMemoryModelSource(replacedPomContent), userProperties);
  }

  public Model loadEffectiveModel(ModelSource2 modelSource, Properties userProperties) {
    final var builder = new DefaultModelBuilderFactory().newInstance();
    final ModelBuildingRequest request = new DefaultModelBuildingRequest();
    request.setModelSource(modelSource);
    request.setUserProperties(userProperties);
    request.setLocationTracking(true);
    request.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
    request.setModelResolver(modelResolver);
    request.setActiveProfileIds(List.of());
    request.setProcessPlugins(false);

    final ModelBuildingResult result;
    try {
      result = builder.build(request);
    } catch (ModelBuildingException e) {
      throw new PomException(
          String.format("Unable to create model building request: %s", e.getMessage()), e);
    }

    return result.getEffectiveModel();
  }
}
