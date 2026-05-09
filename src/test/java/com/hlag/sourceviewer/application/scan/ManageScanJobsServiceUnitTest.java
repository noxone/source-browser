package com.hlag.sourceviewer.application.scan;

import com.hlag.sourceviewer.domain.model.identifier.CommitSha;
import com.hlag.sourceviewer.domain.model.identifier.ErrorMessage;
import com.hlag.sourceviewer.domain.model.identifier.RepositoryIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.ScanJobIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.TokenCount;
import com.hlag.sourceviewer.domain.model.source.ScanJob;
import com.hlag.sourceviewer.domain.port.outgoing.ScanJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ManageScanJobsService}.
 */
class ManageScanJobsServiceUnitTest {

    private ScanJobRepository scanJobRepository;
    private ManageScanJobsService service;

    @BeforeEach
    void setUp() {
        scanJobRepository = mock(ScanJobRepository.class);
        service = new ManageScanJobsService(scanJobRepository);
    }

    // ── listScanJobs ──────────────────────────────────────────────────────────

    @Test
    void listScanJobs_without_filter_returns_all_jobs() {
        var job1 = queuedJob(1L);
        var job2 = runningJob(2L);
        when(scanJobRepository.findAllScanJobs()).thenReturn(List.of(job1, job2));

        var result = service.listScanJobs(Optional.empty());

        assertThat(result).containsExactly(job1, job2);
        verify(scanJobRepository).findAllScanJobs();
        verify(scanJobRepository, never()).findByStatus(any());
    }

    @Test
    void listScanJobs_with_status_filter_delegates_to_findByStatus() {
        var job = queuedJob(1L);
        when(scanJobRepository.findByStatus(ScanJob.ScanJobStatus.QUEUED)).thenReturn(List.of(job));

        var result = service.listScanJobs(Optional.of(ScanJob.ScanJobStatus.QUEUED));

        assertThat(result).containsExactly(job);
        verify(scanJobRepository).findByStatus(ScanJob.ScanJobStatus.QUEUED);
        verify(scanJobRepository, never()).findAllScanJobs();
    }

    @Test
    void listScanJobs_returns_empty_list_when_no_jobs_found() {
        when(scanJobRepository.findAllScanJobs()).thenReturn(List.of());

        var result = service.listScanJobs(Optional.empty());

        assertThat(result).isEmpty();
    }

    // ── deleteScanJob ─────────────────────────────────────────────────────────

    @Test
    void deleteScanJob_deletes_queued_job_successfully() {
        var id = new ScanJobIdentifier(42L);
        var job = queuedJob(42L);
        when(scanJobRepository.findByIdentifier(id)).thenReturn(Optional.of(job));

        service.deleteScanJob(id);

        verify(scanJobRepository).deleteById(id);
    }

    @Test
    void deleteScanJob_throws_when_job_not_found() {
        var id = new ScanJobIdentifier(99L);
        when(scanJobRepository.findByIdentifier(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteScanJob(id))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("99");

        verify(scanJobRepository, never()).deleteById(any());
    }

    @Test
    void deleteScanJob_throws_when_job_is_running() {
        var id = new ScanJobIdentifier(7L);
        var job = runningJob(7L);
        when(scanJobRepository.findByIdentifier(id)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.deleteScanJob(id))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RUNNING");

        verify(scanJobRepository, never()).deleteById(any());
    }

    @Test
    void deleteScanJob_throws_when_job_is_done() {
        var id = new ScanJobIdentifier(8L);
        var job = jobWithStatus(8L, ScanJob.ScanJobStatus.DONE);
        when(scanJobRepository.findByIdentifier(id)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.deleteScanJob(id))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DONE");

        verify(scanJobRepository, never()).deleteById(any());
    }

    @Test
    void deleteScanJob_throws_when_job_is_failed() {
        var id = new ScanJobIdentifier(9L);
        var job = jobWithStatus(9L, ScanJob.ScanJobStatus.FAILED);
        when(scanJobRepository.findByIdentifier(id)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.deleteScanJob(id))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FAILED");
    }

    // ── deleteAllQueuedScanJobs ───────────────────────────────────────────────

    @Test
    void deleteAllQueuedScanJobs_delegates_to_repository() {
        service.deleteAllQueuedScanJobs();

        verify(scanJobRepository).deleteAllQueued();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ScanJob queuedJob(Long id) {
        return jobWithStatus(id, ScanJob.ScanJobStatus.QUEUED);
    }

    private ScanJob runningJob(Long id) {
        return jobWithStatus(id, ScanJob.ScanJobStatus.RUNNING);
    }

    private ScanJob jobWithStatus(Long id, ScanJob.ScanJobStatus status) {
        var job = new ScanJob(
                new RepositoryIdentifier(1L),
                ScanJob.TriggerType.MANUAL,
                Optional.of(new CommitSha("abc1234")),
                status,
                Instant.now(),
                status == ScanJob.ScanJobStatus.RUNNING || status == ScanJob.ScanJobStatus.DONE || status == ScanJob.ScanJobStatus.FAILED
                        ? Optional.of(Instant.now()) : Optional.empty(),
                status == ScanJob.ScanJobStatus.DONE || status == ScanJob.ScanJobStatus.FAILED
                        ? Optional.of(Instant.now()) : Optional.empty(),
                new TokenCount(0),
                status == ScanJob.ScanJobStatus.FAILED
                        ? Optional.of(new ErrorMessage("error")) : Optional.empty()
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
