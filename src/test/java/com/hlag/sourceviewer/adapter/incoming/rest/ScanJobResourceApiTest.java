package com.hlag.sourceviewer.adapter.incoming.rest;

import com.hlag.sourceviewer.domain.model.identifier.CommitSha;
import com.hlag.sourceviewer.domain.model.identifier.RepositoryIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.ScanJobIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.TokenCount;
import com.hlag.sourceviewer.domain.model.source.ScanJob;
import com.hlag.sourceviewer.domain.port.incoming.ManageScanJobsUseCase;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * API-level tests for {@code /api/admin/scan-jobs} using a mocked service layer.
 *
 * <p>Runs the Quarkus HTTP layer end-to-end (routing, serialization, status codes)
 * with {@link ManageScanJobsUseCase} replaced by a Mockito mock, so no database
 * is required.</p>
 */
@QuarkusTest
@TestProfile(ScanJobResourceApiTest.NoDatabaseProfile.class)
@TestSecurity(user = "testuser", roles = {"admin"})
class ScanJobResourceApiTest {

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
                "quarkus.flyway.enabled", "false"
            );
        }
    }

    private static final String BASE_PATH = "/api/admin/scan-jobs";

    @InjectMock
    ManageScanJobsUseCase useCase;

    // ── GET /api/admin/scan-jobs ───────────────────────────────────────────────

    @Test
    void listScanJobs_returns_200_with_empty_array() {
        when(useCase.listScanJobs(Optional.empty())).thenReturn(List.of());

        given()
            .when().get(BASE_PATH)
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$", hasSize(0));
    }

    @Test
    void listScanJobs_returns_all_jobs_with_correct_fields() {
        var job = queuedJob(1L, 42L);
        when(useCase.listScanJobs(Optional.empty())).thenReturn(List.of(job));

        given()
            .when().get(BASE_PATH)
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$", hasSize(1))
            .body("[0].id", equalTo(1))
            .body("[0].repositoryId", equalTo(42))
            .body("[0].triggerType", equalTo("MANUAL"))
            .body("[0].commitSha", equalTo("abc1234"))
            .body("[0].status", equalTo("QUEUED"))
            .body("[0].queuedAt", notNullValue());
    }

    @Test
    void listScanJobs_with_status_filter_passes_filter_to_use_case() {
        var job = queuedJob(2L, 10L);
        when(useCase.listScanJobs(Optional.of(ScanJob.ScanJobStatus.QUEUED))).thenReturn(List.of(job));

        given()
            .queryParam("status", "QUEUED")
            .when().get(BASE_PATH)
            .then()
            .statusCode(200)
            .body("$", hasSize(1));

        verify(useCase).listScanJobs(Optional.of(ScanJob.ScanJobStatus.QUEUED));
    }

    @Test
    void listScanJobs_with_invalid_status_returns_500() {
        given()
            .queryParam("status", "INVALID_STATUS")
            .when().get(BASE_PATH)
            .then()
            .statusCode(greaterThanOrEqualTo(400));
    }

    // ── DELETE /api/admin/scan-jobs/{id} ──────────────────────────────────────

    @Test
    void deleteScanJob_returns_204_on_success() {
        doNothing().when(useCase).deleteScanJob(new ScanJobIdentifier(5L));

        given()
            .when().delete(BASE_PATH + "/5")
            .then()
            .statusCode(204);

        verify(useCase).deleteScanJob(new ScanJobIdentifier(5L));
    }

    @Test
    void deleteScanJob_returns_404_when_job_not_found() {
        doThrow(new NoSuchElementException("Scan job not found: 99"))
                .when(useCase).deleteScanJob(new ScanJobIdentifier(99L));

        given()
            .when().delete(BASE_PATH + "/99")
            .then()
            .statusCode(404);
    }

    @Test
    void deleteScanJob_returns_409_when_job_is_not_queued() {
        doThrow(new IllegalStateException("Scan job 3 cannot be deleted — status is RUNNING"))
                .when(useCase).deleteScanJob(new ScanJobIdentifier(3L));

        given()
            .when().delete(BASE_PATH + "/3")
            .then()
            .statusCode(409);
    }

    // ── DELETE /api/admin/scan-jobs ───────────────────────────────────────────

    @Test
    void deleteAllQueuedScanJobs_returns_204() {
        doNothing().when(useCase).deleteAllQueuedScanJobs();

        given()
            .when().delete(BASE_PATH)
            .then()
            .statusCode(204);

        verify(useCase).deleteAllQueuedScanJobs();
    }

    // ── Access control ────────────────────────────────────────────────────────

    @Test
    @TestSecurity(user = "regularuser", roles = {"user"})
    void listScanJobs_returns_403_for_non_admin() {
        given()
            .when().get(BASE_PATH)
            .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(user = "regularuser", roles = {"user"})
    void deleteScanJob_returns_403_for_non_admin() {
        given()
            .when().delete(BASE_PATH + "/1")
            .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(user = "regularuser", roles = {"user"})
    void deleteAllQueuedScanJobs_returns_403_for_non_admin() {
        given()
            .when().delete(BASE_PATH)
            .then()
            .statusCode(403);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ScanJob queuedJob(Long id, Long repositoryId) {
        var job = new ScanJob(
                new RepositoryIdentifier(repositoryId),
                ScanJob.TriggerType.MANUAL,
                Optional.of(new CommitSha("abc1234")),
                ScanJob.ScanJobStatus.QUEUED,
                Instant.now(),
                Optional.empty(),
                Optional.empty(),
                new TokenCount(0),
                Optional.empty(),
                false
        );
        setId(job, id);
        return job;
    }

    private static void setId(ScanJob job, Long id) {
        try {
            Field field = ScanJob.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(job, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set ScanJob id via reflection", e);
        }
    }
}
