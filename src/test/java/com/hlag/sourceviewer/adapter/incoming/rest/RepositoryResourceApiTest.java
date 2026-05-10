package com.hlag.sourceviewer.adapter.incoming.rest;

import com.hlag.sourceviewer.domain.model.identifier.BranchName;
import com.hlag.sourceviewer.domain.model.identifier.CommitSha;
import com.hlag.sourceviewer.domain.model.identifier.DisplayName;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import com.hlag.sourceviewer.domain.model.identifier.RepositoryIdentifier;
import com.hlag.sourceviewer.domain.model.repository.Repository;
import com.hlag.sourceviewer.domain.port.incoming.ManageRepositoriesUseCase;
import com.hlag.sourceviewer.domain.port.incoming.ManageRepositoriesUseCase.CreateRepositoryCommand;
import com.hlag.sourceviewer.domain.port.incoming.ManageRepositoriesUseCase.UpdateRepositoryCommand;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * API-level tests for {@code /api/repositories} using a mocked service layer.
 *
 * <p>Runs the Quarkus HTTP layer end-to-end (routing, serialization, status codes)
 * with the {@link ManageRepositoriesUseCase} replaced by a Mockito mock,
 * so no database is required.</p>
 */
@QuarkusTest
@TestProfile(RepositoryResourceApiTest.NoDatabaseProfile.class)
@TestSecurity(user = "testuser", roles = {"admin"})
class RepositoryResourceApiTest {

    /** Test profile that disables all database-related infrastructure. */
    public static class NoDatabaseProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "quarkus.datasource.devservices.enabled", "false",
                "quarkus.datasource.jdbc.url", "jdbc:postgresql://localhost:15432/test_placeholder",
                "quarkus.datasource.jdbc.min-size", "0",
                "quarkus.datasource.jdbc.max-size", "1",
                "quarkus.hibernate-orm.database.generation", "none",
                "quarkus.flyway.enabled", "false"
            );
        }
    }

    private static final String BASE_PATH = "/api/repositories";

    @InjectMock
    ManageRepositoriesUseCase useCase;

    // ── GET /api/repositories ─────────────────────────────────────────────────

    @Test
    void listRepositories_returns_200_with_empty_array() {
        when(useCase.listRepositories()).thenReturn(List.of());

        given()
            .when().get(BASE_PATH)
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$", hasSize(0));
    }

    @Test
    void listRepositories_returns_all_repositories_with_all_fields() {
        var scannedAt = Instant.parse("2024-03-15T08:00:00Z");
        var repo = repository(1L, "my-repo", "https://github.com/org/repo.git", "main",
                Optional.of(scannedAt), Optional.of(new CommitSha("abc1234")));
        when(useCase.listRepositories()).thenReturn(List.of(repo));

        given()
            .when().get(BASE_PATH)
            .then()
            .statusCode(200)
            .body("$", hasSize(1))
            .body("[0].id", equalTo(1))
            .body("[0].name", equalTo("my-repo"))
            .body("[0].remoteUrl", equalTo("https://github.com/org/repo.git"))
            .body("[0].defaultBranch", equalTo("main"))
            .body("[0].lastScannedAt", equalTo(scannedAt.toString()))
            .body("[0].lastCommitSha", equalTo("abc1234"));
    }

    @Test
    void listRepositories_maps_optional_fields_as_null_when_absent() {
        var repo = repository(2L, "local-repo", null, "develop",
                Optional.empty(), Optional.empty());
        when(useCase.listRepositories()).thenReturn(List.of(repo));

        given()
            .when().get(BASE_PATH)
            .then()
            .statusCode(200)
            .body("[0].remoteUrl", nullValue())
            .body("[0].lastScannedAt", nullValue())
            .body("[0].lastCommitSha", nullValue());
    }

    // ── GET /api/repositories/{id} ────────────────────────────────────────────

    @Test
    void getRepository_returns_200_when_found() {
        var identifier = new RepositoryIdentifier(42L);
        var repo = repository(42L, "found-repo", "https://git.example.com/repo.git", "main",
                Optional.empty(), Optional.empty());
        when(useCase.findRepository(identifier)).thenReturn(Optional.of(repo));

        given()
            .when().get(BASE_PATH + "/42")
            .then()
            .statusCode(200)
            .body("id", equalTo(42))
            .body("name", equalTo("found-repo"))
            .body("remoteUrl", equalTo("https://git.example.com/repo.git"))
            .body("defaultBranch", equalTo("main"));
    }

    @Test
    void getRepository_returns_404_when_not_found() {
        when(useCase.findRepository(any())).thenReturn(Optional.empty());

        given()
            .when().get(BASE_PATH + "/999")
            .then()
            .statusCode(404);
    }

    // ── POST /api/repositories ────────────────────────────────────────────────

    @Test
    void createRepository_returns_201_with_location_and_body() {
        var created = repository(7L, "new-repo", "https://github.com/org/new.git", "main",
                Optional.empty(), Optional.empty());
        when(useCase.createRepository(any())).thenReturn(created);

        var payload = """
            {
              "name": "new-repo",
              "remoteUrl": "https://github.com/org/new.git",
              "defaultBranch": "main"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(payload)
        .when()
            .post(BASE_PATH)
        .then()
            .statusCode(201)
            .header("Location", containsString(BASE_PATH + "/7"))
            .body("id", equalTo(7))
            .body("name", equalTo("new-repo"))
            .body("remoteUrl", equalTo("https://github.com/org/new.git"))
            .body("defaultBranch", equalTo("main"));
    }

    @Test
    void createRepository_defaults_to_main_when_defaultBranch_is_null() {
        var created = repository(8L, "no-branch-repo", null, "main",
                Optional.empty(), Optional.empty());
        when(useCase.createRepository(any())).thenReturn(created);

        var payload = """
            {
              "name": "no-branch-repo",
              "remoteUrl": null,
              "defaultBranch": null
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(payload)
        .when()
            .post(BASE_PATH)
        .then()
            .statusCode(201);

        verify(useCase).createRepository(argThat(cmd ->
            cmd.defaultBranch().equals(new BranchName("main"))
        ));
    }

    @Test
    void createRepository_treats_blank_remoteUrl_as_absent() {
        var created = repository(9L, "blank-url-repo", null, "main",
                Optional.empty(), Optional.empty());
        when(useCase.createRepository(any())).thenReturn(created);

        var payload = """
            {
              "name": "blank-url-repo",
              "remoteUrl": "   ",
              "defaultBranch": "main"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(payload)
        .when()
            .post(BASE_PATH)
        .then()
            .statusCode(201);

        verify(useCase).createRepository(argThat((CreateRepositoryCommand cmd) ->
            cmd.remoteUrl().isEmpty()
        ));
    }

    @Test
    void createRepository_without_remoteUrl_sets_empty_optional() {
        var created = repository(10L, "local-only-repo", null, "trunk",
                Optional.empty(), Optional.empty());
        when(useCase.createRepository(any())).thenReturn(created);

        var payload = """
            {
              "name": "local-only-repo",
              "remoteUrl": null,
              "defaultBranch": "trunk"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(payload)
        .when()
            .post(BASE_PATH)
        .then()
            .statusCode(201);

        verify(useCase).createRepository(argThat((CreateRepositoryCommand cmd) ->
            cmd.remoteUrl().isEmpty() && cmd.defaultBranch().equals(new BranchName("trunk"))
        ));
    }

    // ── PUT /api/repositories/{id} ────────────────────────────────────────────

    @Test
    void updateRepository_returns_200_with_updated_values() {
        var updated = repository(5L, "updated-name", null, "feature",
                Optional.empty(), Optional.empty());
        when(useCase.updateRepository(any())).thenReturn(updated);

        var payload = """
            {
              "name": "updated-name",
              "remoteUrl": null,
              "defaultBranch": "feature"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(payload)
        .when()
            .put(BASE_PATH + "/5")
        .then()
            .statusCode(200)
            .body("name", equalTo("updated-name"))
            .body("defaultBranch", equalTo("feature"));
    }

    @Test
    void updateRepository_passes_correct_identifier_to_use_case() {
        var updated = repository(11L, "renamed", null, "main",
                Optional.empty(), Optional.empty());
        when(useCase.updateRepository(any())).thenReturn(updated);

        var payload = """
            {
              "name": "renamed",
              "remoteUrl": null,
              "defaultBranch": "main"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(payload)
        .when()
            .put(BASE_PATH + "/11")
        .then()
            .statusCode(200);

        verify(useCase).updateRepository(argThat((UpdateRepositoryCommand cmd) ->
            cmd.identifier().equals(new RepositoryIdentifier(11L))
        ));
    }

    @Test
    void updateRepository_returns_404_when_not_found() {
        when(useCase.updateRepository(any())).thenThrow(new NoSuchElementException("not found"));

        var payload = """
            {
              "name": "ghost",
              "remoteUrl": null,
              "defaultBranch": "main"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(payload)
        .when()
            .put(BASE_PATH + "/999")
        .then()
            .statusCode(404);
    }

    // ── DELETE /api/repositories/{id} ─────────────────────────────────────────

    @Test
    void deleteRepository_returns_204() {
        doNothing().when(useCase).deleteRepository(any());

        given()
            .when().delete(BASE_PATH + "/1")
            .then()
            .statusCode(204);
    }

    @Test
    void deleteRepository_passes_correct_identifier_to_use_case() {
        doNothing().when(useCase).deleteRepository(any());

        given()
            .when().delete(BASE_PATH + "/42")
            .then()
            .statusCode(204);

        verify(useCase).deleteRepository(new RepositoryIdentifier(42L));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Repository repository(long id, String name, String remoteUrl, String defaultBranch,
                                  Optional<Instant> lastScannedAt, Optional<CommitSha> lastCommitSha) {
        var repo = new Repository(
                new DisplayName(name),
                Optional.ofNullable(remoteUrl).map(FilePath::new),
                new BranchName(defaultBranch),
                lastScannedAt,
                lastCommitSha,
                Optional.empty()
        );
        try {
            var field = repo.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(repo, id);
        } catch (Exception e) {
            throw new RuntimeException("Could not set id for test repository", e);
        }
        return repo;
    }
}
