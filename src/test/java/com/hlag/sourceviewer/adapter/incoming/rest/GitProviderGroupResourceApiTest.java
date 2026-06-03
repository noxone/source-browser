package com.hlag.sourceviewer.adapter.incoming.rest;

import com.hlag.sourceviewer.domain.model.identifier.DisplayName;
import com.hlag.sourceviewer.domain.model.identifier.GitProviderGroupIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.GitProviderType;
import com.hlag.sourceviewer.domain.model.identifier.GroupPath;
import com.hlag.sourceviewer.domain.model.repository.GitProviderGroup;
import com.hlag.sourceviewer.domain.port.incoming.ManageGitCredentialsUseCase;
import com.hlag.sourceviewer.domain.port.incoming.ManageGitProviderGroupsUseCase;
import com.hlag.sourceviewer.domain.port.incoming.SyncGroupRepositoriesUseCase;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * API-level tests for {@code /api/git-provider-groups} using a mocked service layer.
 *
 * <p>Runs the Quarkus HTTP layer end-to-end (routing, serialization, status codes)
 * with the {@link ManageGitProviderGroupsUseCase} replaced by a Mockito mock,
 * so no database is required.</p>
 */
@QuarkusTest
@TestProfile(GitProviderGroupResourceApiTest.NoDatabaseProfile.class)
@TestSecurity(user = "testuser", roles = {"admin"})
class GitProviderGroupResourceApiTest {

    /** Test profile that disables all database-related infrastructure. */
    public static class NoDatabaseProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "quarkus.datasource.devservices.enabled", "false",
                "quarkus.datasource.jdbc.url", "jdbc:postgresql://localhost:15432/test_placeholder",
                "quarkus.datasource.jdbc.min-size", "0",
                "quarkus.datasource.jdbc.max-size", "1",
                "quarkus.hibernate-orm.schema-management.strategy", "none",
                "quarkus.flyway.enabled", "false",
                "quarkus.scheduler.enabled", "false"
            );
        }
    }

    private static final String BASE_PATH = "/api/git-provider-groups";

    @InjectMock
    ManageGitProviderGroupsUseCase useCase;

    @InjectMock
    ManageGitCredentialsUseCase credentialsUseCase;

    @InjectMock
    SyncGroupRepositoriesUseCase syncUseCase;

    // ── GET /api/git-provider-groups ──────────────────────────────────────────

    @Test
    void listGitProviderGroups_returns_200_with_empty_array() {
        when(useCase.listGitProviderGroups()).thenReturn(List.of());

        given()
            .when().get(BASE_PATH)
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$", hasSize(0));
    }

    @Test
    void listGitProviderGroups_returns_all_configured_groups() {
        var group = gitLabGroup(1L, "my-group", "my-org/sub", false, true);
        when(useCase.listGitProviderGroups()).thenReturn(List.of(group));

        given()
            .when().get(BASE_PATH)
            .then()
            .statusCode(200)
            .body("$", hasSize(1))
            .body("[0].id", equalTo(1))
            .body("[0].name", equalTo("my-group"))
            .body("[0].providerType", equalTo("GITLAB"))
            .body("[0].groupPath", equalTo("my-org/sub"))
            .body("[0].archivedOmitted", equalTo(false))
            .body("[0].forkedOmitted", equalTo(true));
    }

    // ── GET /api/git-provider-groups/{id} ────────────────────────────────────

    @Test
    void getGitProviderGroup_returns_200_when_found() {
        var identifier = new GitProviderGroupIdentifier(42L);
        var group = gitLabGroup(42L, "found-group", "org/group", true, false);
        when(useCase.findGitProviderGroup(identifier)).thenReturn(Optional.of(group));

        given()
            .when().get(BASE_PATH + "/42")
            .then()
            .statusCode(200)
            .body("id", equalTo(42))
            .body("name", equalTo("found-group"))
            .body("groupPath", equalTo("org/group"))
            .body("archivedOmitted", equalTo(true))
            .body("forkedOmitted", equalTo(false));
    }

    @Test
    void getGitProviderGroup_returns_404_when_not_found() {
        when(useCase.findGitProviderGroup(any())).thenReturn(Optional.empty());

        given()
            .when().get(BASE_PATH + "/999")
            .then()
            .statusCode(404);
    }

    // ── POST /api/git-provider-groups ─────────────────────────────────────────

    @Test
    void createGitProviderGroup_returns_201_with_location_and_body() {
        var created = gitLabGroup(7L, "new-group", "my-org/new", true, false);
        when(useCase.createGitProviderGroup(any())).thenReturn(created);

        var payload = """
            {
              "name": "new-group",
              "providerType": "GITLAB",
              "groupPath": "my-org/new",
              "baseUrl": null,
              "archivedOmitted": true,
              "forkedOmitted": false
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
            .body("name", equalTo("new-group"))
            .body("providerType", equalTo("GITLAB"))
            .body("groupPath", equalTo("my-org/new"))
            .body("archivedOmitted", equalTo(true))
            .body("forkedOmitted", equalTo(false));
    }

    @Test
    void createGitProviderGroup_accepts_github_provider_type() {
        var created = gitHubGroup(8L, "my-gh-org", "my-org");
        when(useCase.createGitProviderGroup(any())).thenReturn(created);

        var payload = """
            {
              "name": "my-gh-org",
              "providerType": "GITHUB",
              "groupPath": "my-org",
              "baseUrl": null,
              "archivedOmitted": false,
              "forkedOmitted": false
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(payload)
        .when()
            .post(BASE_PATH)
        .then()
            .statusCode(201)
            .body("providerType", equalTo("GITHUB"));
    }

    @Test
    void createGitProviderGroup_persists_base_url() {
        var created = gitLabGroupWithBaseUrl(9L, "self-hosted", "internal/team", "https://gitlab.example.com");
        when(useCase.createGitProviderGroup(any())).thenReturn(created);

        var payload = """
            {
              "name": "self-hosted",
              "providerType": "GITLAB",
              "groupPath": "internal/team",
              "baseUrl": "https://gitlab.example.com",
              "archivedOmitted": false,
              "forkedOmitted": false
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(payload)
        .when()
            .post(BASE_PATH)
        .then()
            .statusCode(201)
            .body("baseUrl", equalTo("https://gitlab.example.com"));
    }

    // ── PUT /api/git-provider-groups/{id} ────────────────────────────────────

    @Test
    void updateGitProviderGroup_returns_200_with_updated_values() {
        var updated = gitHubGroup(5L, "updated-name", "updated-org");
        when(useCase.updateGitProviderGroup(any())).thenReturn(updated);

        var payload = """
            {
              "name": "updated-name",
              "providerType": "GITHUB",
              "groupPath": "updated-org",
              "baseUrl": null,
              "archivedOmitted": false,
              "forkedOmitted": false
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
            .body("providerType", equalTo("GITHUB"));
    }

    @Test
    void updateGitProviderGroup_returns_404_when_not_found() {
        when(useCase.updateGitProviderGroup(any())).thenThrow(new NoSuchElementException("not found"));

        var payload = """
            {
              "name": "ghost",
              "providerType": "GITLAB",
              "groupPath": "ghost/path",
              "baseUrl": null,
              "archivedOmitted": false,
              "forkedOmitted": false
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

    // ── DELETE /api/git-provider-groups/{id} ─────────────────────────────────

    @Test
    void deleteGitProviderGroup_returns_204() {
        doNothing().when(useCase).deleteGitProviderGroup(any());

        given()
            .when().delete(BASE_PATH + "/1")
            .then()
            .statusCode(204);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private GitProviderGroup gitLabGroup(long id, String name, String path, boolean archivedOmitted, boolean forkedOmitted) {
        return makeGroup(id, name, GitProviderType.GITLAB, path, null, archivedOmitted, forkedOmitted);
    }

    private GitProviderGroup gitHubGroup(long id, String name, String path) {
        return makeGroup(id, name, GitProviderType.GITHUB, path, null, false, false);
    }

    private GitProviderGroup gitLabGroupWithBaseUrl(long id, String name, String path, String baseUrl) {
        return makeGroup(id, name, GitProviderType.GITLAB, path, baseUrl, false, false);
    }

    private GitProviderGroup makeGroup(long id, String name, GitProviderType type, String path,
                                       String baseUrl, boolean archivedOmitted, boolean forkedOmitted) {
        var group = new GitProviderGroup(
                new DisplayName(name),
                type,
                new GroupPath(path),
                Optional.ofNullable(baseUrl).map(com.hlag.sourceviewer.domain.model.identifier.FilePath::new),
                archivedOmitted,
                forkedOmitted,
                false,
                false
        );
        try {
            var field = group.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(group, id);
        } catch (Exception e) {
            throw new RuntimeException("Could not set id for test group", e);
        }
        return group;
    }
}
