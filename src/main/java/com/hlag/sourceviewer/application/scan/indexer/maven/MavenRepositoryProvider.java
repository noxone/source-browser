package com.hlag.sourceviewer.application.scan.indexer.maven;

import com.hlag.sourceviewer.domain.port.incoming.ManageAppSettingsUseCase;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import java.util.List;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.repository.AuthenticationBuilder;

@Dependent
public class MavenRepositoryProvider {
  private final ManageAppSettingsUseCase settings;

  @Inject
  public MavenRepositoryProvider(ManageAppSettingsUseCase settings) {
    this.settings = settings;
  }

  public List<RemoteRepository> buildRemoteRepositories() {
    String url      = settings.getSetting(ManageAppSettingsUseCase.SETTING_MAVEN_REPO_URL,
        ManageAppSettingsUseCase.DEFAULT_MAVEN_REPO_URL);
    String username = settings.getSetting(ManageAppSettingsUseCase.SETTING_MAVEN_REPO_USERNAME,
        ManageAppSettingsUseCase.DEFAULT_MAVEN_REPO_USERNAME);
    String password = settings.getSetting(ManageAppSettingsUseCase.SETTING_MAVEN_REPO_PASSWORD,
        ManageAppSettingsUseCase.DEFAULT_MAVEN_REPO_PASSWORD);

    var builder = new RemoteRepository.Builder("central", "default", url);
    if (!username.isEmpty()) {
      Authentication auth = new AuthenticationBuilder()
          .addUsername(username).addPassword(password).build();
      builder.setAuthentication(auth);
    }
    return List.of(builder.build());
  }

  public LocalRepository buildLocalRepository() {
    String localRepoPath = System.getProperty("user.home") + "/.m2/repository";
    // "simple" avoids the EnhancedLocalRepositoryManager's dependency on the
    // "gaecv" NameMapper, which is absent when maven-resolver artifacts end up
    // at mixed versions due to the Quarkus BOM. Simple LRM is sufficient for
    // read/download operations — it uses the same standard M2 directory layout.
    return new LocalRepository(new java.io.File(localRepoPath), "simple");
  }
}
