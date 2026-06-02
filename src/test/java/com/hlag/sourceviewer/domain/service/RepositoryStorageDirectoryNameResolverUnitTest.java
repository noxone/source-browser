package com.hlag.sourceviewer.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hlag.sourceviewer.domain.model.identifier.BranchName;
import com.hlag.sourceviewer.domain.model.identifier.DisplayName;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import com.hlag.sourceviewer.domain.model.repository.Repository;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RepositoryStorageDirectoryNameResolverUnitTest {

    @Test
    void local_directory_name_contains_repository_identifier_and_sanitized_remote_path() {
        Repository repository = new Repository(
                new DisplayName("core"),
                Optional.of(new FilePath("https://github.com/acme/team-repo.git")),
                new BranchName("main"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
        setIdentifier(repository, 42L);

        String directoryName = RepositoryStorageDirectoryNameResolver.localDirectoryName(repository);

        assertThat(directoryName).isEqualTo("repo-42-acme-team-repo");
    }

    @Test
    void local_directory_name_falls_back_to_identifier_when_remote_url_is_missing() {
        Repository repository = new Repository(
                new DisplayName("core"),
                Optional.empty(),
                new BranchName("main"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
        setIdentifier(repository, 7L);

        String directoryName = RepositoryStorageDirectoryNameResolver.localDirectoryName(repository);

        assertThat(directoryName).isEqualTo("repo-7");
    }

    private static void setIdentifier(Repository repository, Long identifier) {
        try {
            Field idField = Repository.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(repository, identifier);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not assign repository identifier in test", exception);
        }
    }
}

