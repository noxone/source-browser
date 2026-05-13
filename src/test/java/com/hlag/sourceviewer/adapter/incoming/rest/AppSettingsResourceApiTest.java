package com.hlag.sourceviewer.adapter.incoming.rest;

import com.hlag.sourceviewer.domain.model.setting.AppSetting;
import com.hlag.sourceviewer.domain.port.incoming.ManageAppSettingsUseCase;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

/**
 * API-level tests for {@code /api/admin/settings} using a mocked service layer.
 *
 * <p>Validates routing, serialization, status codes, and access control without
 * requiring a real database.</p>
 */
@QuarkusTest
@TestProfile(AppSettingsResourceApiTest.NoDatabaseProfile.class)
@TestSecurity(user = "admin", roles = {"admin"})
class AppSettingsResourceApiTest {

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

    private static final String BASE_PATH = "/api/admin/settings";

    @InjectMock
    ManageAppSettingsUseCase useCase;

    // ── GET /api/admin/settings ───────────────────────────────────────────────

    @Test
    void listSettings_returns_200_with_all_known_settings() {
        when(useCase.getSetting(any(), any())).thenAnswer(inv -> inv.getArgument(1));
        when(useCase.getSetting(ManageAppSettingsUseCase.SETTING_SCAN_MAX_PARALLEL_JOBS,
                ManageAppSettingsUseCase.DEFAULT_SCAN_MAX_PARALLEL_JOBS))
                .thenReturn("2");

        given()
            .when().get(BASE_PATH)
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$", hasSize(greaterThan(0)))
            .body("[0].key", equalTo(ManageAppSettingsUseCase.SETTING_SCAN_MAX_PARALLEL_JOBS))
            .body("[0].value", equalTo("2"))
            .body("[0].description", notNullValue());
    }

    @Test
    void listSettings_returns_default_value_when_not_stored() {
        when(useCase.getSetting(any(), any())).thenAnswer(inv -> inv.getArgument(1));
        when(useCase.getSetting(ManageAppSettingsUseCase.SETTING_SCAN_MAX_PARALLEL_JOBS,
                ManageAppSettingsUseCase.DEFAULT_SCAN_MAX_PARALLEL_JOBS))
                .thenReturn(ManageAppSettingsUseCase.DEFAULT_SCAN_MAX_PARALLEL_JOBS);

        given()
            .when().get(BASE_PATH)
            .then()
            .statusCode(200)
            .body("[0].value", equalTo(ManageAppSettingsUseCase.DEFAULT_SCAN_MAX_PARALLEL_JOBS));
    }

    // ── PUT /api/admin/settings/{key} ─────────────────────────────────────────

    @Test
    void updateSetting_returns_204_for_known_key() {
        doNothing().when(useCase).setSetting(any(), any());

        given()
            .contentType(ContentType.JSON)
            .body("""
                    {"key": "%s", "value": "4", "description": ""}
                    """.formatted(ManageAppSettingsUseCase.SETTING_SCAN_MAX_PARALLEL_JOBS))
            .when().put(BASE_PATH + "/" + ManageAppSettingsUseCase.SETTING_SCAN_MAX_PARALLEL_JOBS)
            .then()
            .statusCode(204);

        verify(useCase).setSetting(ManageAppSettingsUseCase.SETTING_SCAN_MAX_PARALLEL_JOBS, "4");
    }

    @Test
    void updateSetting_returns_400_for_unknown_key() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                    {"key": "unknown.setting", "value": "anything", "description": ""}
                    """)
            .when().put(BASE_PATH + "/unknown.setting")
            .then()
            .statusCode(400);

        verify(useCase, never()).setSetting(any(), any());
    }

    // ── Access control ────────────────────────────────────────────────────────

    @Test
    @TestSecurity(user = "regularuser", roles = {"user"})
    void listSettings_returns_403_for_non_admin() {
        given()
            .when().get(BASE_PATH)
            .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(user = "regularuser", roles = {"user"})
    void updateSetting_returns_403_for_non_admin() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                    {"key": "%s", "value": "1", "description": ""}
                    """.formatted(ManageAppSettingsUseCase.SETTING_SCAN_MAX_PARALLEL_JOBS))
            .when().put(BASE_PATH + "/" + ManageAppSettingsUseCase.SETTING_SCAN_MAX_PARALLEL_JOBS)
            .then()
            .statusCode(403);
    }
}
