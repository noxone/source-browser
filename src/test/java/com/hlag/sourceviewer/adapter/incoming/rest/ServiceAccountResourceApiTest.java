package com.hlag.sourceviewer.adapter.incoming.rest;

import com.hlag.sourceviewer.domain.model.identifier.PersonalAccessTokenIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.PrincipalName;
import com.hlag.sourceviewer.domain.model.identifier.TokenHash;
import com.hlag.sourceviewer.domain.model.identifier.TokenName;
import com.hlag.sourceviewer.domain.model.identifier.UserAccountIdentifier;
import com.hlag.sourceviewer.domain.model.token.PersonalAccessToken;
import com.hlag.sourceviewer.domain.model.user.UserAccount;
import com.hlag.sourceviewer.domain.port.incoming.ManagePersonalAccessTokensUseCase.TokenCreationResult;
import com.hlag.sourceviewer.domain.port.incoming.ManageServiceAccountsUseCase;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * API-level tests for {@code /api/admin/service-accounts} using a mocked service layer.
 *
 * <p>Runs the Quarkus HTTP layer end-to-end (routing, serialization, status codes)
 * with the {@link ManageServiceAccountsUseCase} replaced by a Mockito mock,
 * so no database is required.</p>
 */
@QuarkusTest
@TestProfile(ServiceAccountResourceApiTest.NoDatabaseProfile.class)
@TestSecurity(user = "testuser", roles = {"admin"})
class ServiceAccountResourceApiTest {

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

    private static final String BASE_PATH = "/api/admin/service-accounts";

    @InjectMock
    ManageServiceAccountsUseCase useCase;

    // ── GET /api/admin/service-accounts ───────────────────────────────────────

    @Test
    void listServiceAccounts_returns_200_with_empty_array() {
        when(useCase.listServiceAccounts()).thenReturn(List.of());

        given()
            .when().get(BASE_PATH)
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$", hasSize(0));
    }

    @Test
    void listServiceAccounts_returns_all_accounts_with_correct_fields() {
        var account = serviceAccount(1L, "my-bot", false);
        when(useCase.listServiceAccounts()).thenReturn(List.of(account));

        given()
            .when().get(BASE_PATH)
            .then()
            .statusCode(200)
            .body("$", hasSize(1))
            .body("[0].id", equalTo(1))
            .body("[0].name", equalTo("my-bot"))
            .body("[0].admin", equalTo(false));
    }

    @Test
    void listServiceAccounts_strips_svc_prefix_from_name() {
        var account = serviceAccount(2L, "ci-runner", true);
        when(useCase.listServiceAccounts()).thenReturn(List.of(account));

        given()
            .when().get(BASE_PATH)
            .then()
            .statusCode(200)
            .body("[0].name", equalTo("ci-runner"))
            .body("[0].admin", equalTo(true));
    }

    // ── POST /api/admin/service-accounts ──────────────────────────────────────

    @Test
    void createServiceAccount_returns_201_with_location_and_body() {
        var created = serviceAccount(5L, "new-bot", false);
        when(useCase.createServiceAccount(any())).thenReturn(created);

        given()
            .contentType(ContentType.JSON)
            .body("""
                { "name": "new-bot", "admin": false }
                """)
            .when().post(BASE_PATH)
            .then()
            .statusCode(201)
            .header("Location", containsString("/api/admin/service-accounts/5"))
            .body("id", equalTo(5))
            .body("name", equalTo("new-bot"));
    }

    @Test
    void createServiceAccount_passes_name_and_admin_to_use_case() {
        var created = serviceAccount(6L, "admin-bot", true);
        when(useCase.createServiceAccount(any())).thenReturn(created);

        given()
            .contentType(ContentType.JSON)
            .body("""
                { "name": "admin-bot", "admin": true }
                """)
            .when().post(BASE_PATH)
            .then()
            .statusCode(201);

        verify(useCase).createServiceAccount(argThat(cmd ->
                cmd.name().equals("admin-bot") && cmd.admin()
        ));
    }

    @Test
    void createServiceAccount_returns_409_when_name_already_taken() {
        when(useCase.createServiceAccount(any()))
                .thenThrow(new IllegalArgumentException("already exists"));

        given()
            .contentType(ContentType.JSON)
            .body("""
                { "name": "duplicate", "admin": false }
                """)
            .when().post(BASE_PATH)
            .then()
            .statusCode(409);
    }

    // ── PUT /api/admin/service-accounts/{id} ──────────────────────────────────

    @Test
    void updateServiceAccount_returns_200_with_updated_account() {
        var updated = serviceAccount(3L, "my-bot", true);
        when(useCase.setAdminStatus(new UserAccountIdentifier(3L), true)).thenReturn(updated);

        given()
            .contentType(ContentType.JSON)
            .body("""
                { "admin": true }
                """)
            .when().put(BASE_PATH + "/3")
            .then()
            .statusCode(200)
            .body("admin", equalTo(true));
    }

    @Test
    void updateServiceAccount_returns_404_when_not_found() {
        when(useCase.setAdminStatus(any(), anyBoolean()))
                .thenThrow(new NoSuchElementException("not found"));

        given()
            .contentType(ContentType.JSON)
            .body("""
                { "admin": false }
                """)
            .when().put(BASE_PATH + "/999")
            .then()
            .statusCode(404);
    }

    // ── DELETE /api/admin/service-accounts/{id} ───────────────────────────────

    @Test
    void deleteServiceAccount_returns_204() {
        doNothing().when(useCase).deleteServiceAccount(any());

        given()
            .when().delete(BASE_PATH + "/7")
            .then()
            .statusCode(204);

        verify(useCase).deleteServiceAccount(new UserAccountIdentifier(7L));
    }

    // ── GET /api/admin/service-accounts/{id}/tokens ───────────────────────────

    @Test
    void listTokens_returns_200_with_tokens() {
        var account = serviceAccount(1L, "bot", false);
        var token = pat(account, "deploy-key");
        when(useCase.listTokens(new UserAccountIdentifier(1L))).thenReturn(List.of(token));

        given()
            .when().get(BASE_PATH + "/1/tokens")
            .then()
            .statusCode(200)
            .body("$", hasSize(1))
            .body("[0].name", equalTo("deploy-key"))
            .body("[0].expiresAt", nullValue());
    }

    @Test
    void listTokens_returns_404_when_service_account_not_found() {
        when(useCase.listTokens(any())).thenThrow(new NoSuchElementException());

        given()
            .when().get(BASE_PATH + "/999/tokens")
            .then()
            .statusCode(404);
    }

    // ── POST /api/admin/service-accounts/{id}/tokens ──────────────────────────

    @Test
    void createToken_returns_201_with_raw_token() {
        var account = serviceAccount(1L, "bot", false);
        var token = pat(account, "ci-token");
        when(useCase.createToken(eq(new UserAccountIdentifier(1L)), any()))
                .thenReturn(new TokenCreationResult(token, "svt_supersecret"));

        given()
            .contentType(ContentType.JSON)
            .body("""
                { "name": "ci-token", "expiresAt": null }
                """)
            .when().post(BASE_PATH + "/1/tokens")
            .then()
            .statusCode(201)
            .body("rawToken", equalTo("svt_supersecret"))
            .body("token.name", equalTo("ci-token"));
    }

    @Test
    void createToken_returns_404_when_service_account_not_found() {
        when(useCase.createToken(any(), any())).thenThrow(new NoSuchElementException());

        given()
            .contentType(ContentType.JSON)
            .body("""
                { "name": "x", "expiresAt": null }
                """)
            .when().post(BASE_PATH + "/999/tokens")
            .then()
            .statusCode(404);
    }

    // ── DELETE /api/admin/service-accounts/{id}/tokens/{tokenId} ─────────────

    @Test
    void revokeToken_returns_204() {
        doNothing().when(useCase).revokeToken(any());

        given()
            .when().delete(BASE_PATH + "/1/tokens/42")
            .then()
            .statusCode(204);

        verify(useCase).revokeToken(new PersonalAccessTokenIdentifier(42L));
    }

    // ── Security: non-admin must not access admin endpoints ───────────────────

    @Test
    @TestSecurity(user = "regularuser", roles = {"user"})
    void listServiceAccounts_returns_403_for_non_admin() {
        given()
            .when().get(BASE_PATH)
            .then()
            .statusCode(403);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private UserAccount serviceAccount(Long id, String name, boolean admin) {
        var account = new UserAccount(new PrincipalName("svc:" + name), admin, true, Instant.now());
        // Simulate Hibernate-assigned id via reflection so identifier() is non-null
        try {
            var field = UserAccount.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(account, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return account;
    }

    private PersonalAccessToken pat(UserAccount owner, String name) {
        var token = new PersonalAccessToken(
                owner.principalName(),
                new TokenName(name),
                new TokenHash("a".repeat(64)),
                Instant.now(),
                Optional.empty());
        try {
            var field = PersonalAccessToken.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(token, 1L);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return token;
    }
}
