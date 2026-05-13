package com.hlag.sourceviewer.application.scan;

import com.hlag.sourceviewer.application.scan.indexer.LanguageIndexerRegistry;
import com.hlag.sourceviewer.domain.model.identifier.CommitSha;
import com.hlag.sourceviewer.domain.model.identifier.RepositoryIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.ScanJobIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.TokenCount;
import com.hlag.sourceviewer.domain.model.repository.Repository;
import com.hlag.sourceviewer.domain.model.source.ScanJob;
import com.hlag.sourceviewer.domain.port.outgoing.DocumentRepository;
import com.hlag.sourceviewer.domain.port.outgoing.GitAccess;
import com.hlag.sourceviewer.domain.port.outgoing.RepositoryStore;
import com.hlag.sourceviewer.domain.port.outgoing.ScanJobRepository;
import com.hlag.sourceviewer.domain.port.outgoing.SourceFileRepository;
import com.hlag.sourceviewer.domain.port.incoming.ManageAppSettingsUseCase;
import com.hlag.sourceviewer.domain.port.outgoing.SymbolRepository;
import com.hlag.sourceviewer.domain.port.outgoing.SymbolReferenceRepository;
import jakarta.transaction.TransactionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ExecuteScanJobService}.
 *
 * <p>Verifies the job-execution lifecycle: claiming, success, and failure paths.</p>
 */
class ExecuteScanJobServiceUnitTest {

    private ScanJobRepository scanJobRepository;
    private RepositoryStore repositoryStore;
    private GitAccess gitAccess;
    private SourceFileRepository sourceFileRepository;
    private DocumentRepository documentRepository;
    private TransactionManager transactionManager;
    private SymbolRepository symbolRepository;
    private SymbolReferenceRepository symbolReferenceRepository;
    private ManageAppSettingsUseCase manageAppSettings;
    private LanguageIndexerRegistry languageIndexerRegistry;
    private ExecuteScanJobService service;

    @BeforeEach
    void setUp() {
        scanJobRepository = mock(ScanJobRepository.class);
        repositoryStore = mock(RepositoryStore.class);
        gitAccess = mock(GitAccess.class);
        sourceFileRepository = mock(SourceFileRepository.class);
        documentRepository = mock(DocumentRepository.class);
        transactionManager = mock(TransactionManager.class);
        symbolRepository = mock(SymbolRepository.class);
        symbolReferenceRepository = mock(SymbolReferenceRepository.class);
        manageAppSettings = mock(ManageAppSettingsUseCase.class);
        languageIndexerRegistry = mock(LanguageIndexerRegistry.class);
        when(manageAppSettings.getSetting(
                ManageAppSettingsUseCase.SETTING_SCAN_BATCH_SIZE,
                ManageAppSettingsUseCase.DEFAULT_SCAN_BATCH_SIZE))
                .thenReturn(ManageAppSettingsUseCase.DEFAULT_SCAN_BATCH_SIZE);
        when(languageIndexerRegistry.selectAndPrepare(any(), any())).thenReturn(Map.of());
        service = new ExecuteScanJobService(
                scanJobRepository, repositoryStore, gitAccess,
                sourceFileRepository, documentRepository, transactionManager,
                symbolRepository, symbolReferenceRepository,
                manageAppSettings, languageIndexerRegistry);
    }

    // ── tryExecuteNextJob — no job available ──────────────────────────────────

    @Test
    void tryExecuteNextJob_returns_false_when_no_job_is_queued() {
        when(scanJobRepository.pollNextQueued()).thenReturn(Optional.empty());

        boolean result = service.tryExecuteNextJob();

        assertThat(result).isFalse();
        verifyNoInteractions(repositoryStore);
    }

    // ── tryExecuteNextJob — happy path ────────────────────────────────────────

    @Test
    void tryExecuteNextJob_returns_true_and_marks_job_done_on_success() {
        var job = queuedJob(10L, 99L);
        var repo = mock(Repository.class);

        when(scanJobRepository.pollNextQueued()).thenReturn(Optional.of(job));
        when(repositoryStore.findByIdentifier(new RepositoryIdentifier(99L)))
                .thenReturn(Optional.of(repo));
        when(repo.name()).thenReturn(new com.hlag.sourceviewer.domain.model.identifier.DisplayName("my-repo"));
        doNothing().when(scanJobRepository).update(any());
        when(scanJobRepository.findByIdentifier(new ScanJobIdentifier(10L)))
                .thenReturn(Optional.of(job));

        boolean result = service.tryExecuteNextJob();

        assertThat(result).isTrue();
        assertThat(job.status()).isEqualTo(ScanJob.ScanJobStatus.DONE);
        assertThat(job.startedAt()).isPresent();
        assertThat(job.finishedAt()).isPresent();
    }

    @Test
    void tryExecuteNextJob_activates_symbols_and_documents_after_successful_scan() {
        var job = queuedJob(10L, 99L);
        var repo = mock(Repository.class);

        when(scanJobRepository.pollNextQueued()).thenReturn(Optional.of(job));
        when(repositoryStore.findByIdentifier(new RepositoryIdentifier(99L)))
                .thenReturn(Optional.of(repo));
        when(repo.name()).thenReturn(new com.hlag.sourceviewer.domain.model.identifier.DisplayName("my-repo"));
        doNothing().when(scanJobRepository).update(any());
        when(scanJobRepository.findByIdentifier(new ScanJobIdentifier(10L)))
                .thenReturn(Optional.of(job));

        service.tryExecuteNextJob();

        verify(symbolRepository).publishByScanJob(10L);
        verify(symbolReferenceRepository).publishByScanJob(10L);
        verify(symbolRepository).deleteSupersededByScanJob(10L);
        verify(symbolReferenceRepository).deleteSupersededByScanJob(10L);
        verify(documentRepository).publishByScanJob(10L);
        verify(documentRepository).deleteSupersededDocuments(10L);
        verify(symbolRepository, never()).deleteUnpublishedByScanJob(anyLong());
        verify(symbolReferenceRepository, never()).deleteUnpublishedByScanJob(anyLong());
        verify(documentRepository, never()).deleteUnpublishedByScanJob(anyLong());
    }

    // ── tryExecuteNextJob — failure path ──────────────────────────────────────

    @Test
    void tryExecuteNextJob_marks_job_failed_when_repository_not_found() {
        var job = queuedJob(11L, 55L);

        when(scanJobRepository.pollNextQueued()).thenReturn(Optional.of(job));
        when(repositoryStore.findByIdentifier(new RepositoryIdentifier(55L)))
                .thenReturn(Optional.empty());
        doNothing().when(scanJobRepository).update(any());
        when(scanJobRepository.findByIdentifier(new ScanJobIdentifier(11L)))
                .thenReturn(Optional.of(job));

        boolean result = service.tryExecuteNextJob();

        assertThat(result).isTrue();
        assertThat(job.status()).isEqualTo(ScanJob.ScanJobStatus.FAILED);
        assertThat(job.errorMessage()).isPresent();
    }

    @Test
    void tryExecuteNextJob_cleans_up_unpublished_symbols_and_documents_on_failure() {
        var job = queuedJob(11L, 55L);

        when(scanJobRepository.pollNextQueued()).thenReturn(Optional.of(job));
        when(repositoryStore.findByIdentifier(new RepositoryIdentifier(55L)))
                .thenReturn(Optional.empty());
        doNothing().when(scanJobRepository).update(any());
        when(scanJobRepository.findByIdentifier(new ScanJobIdentifier(11L)))
                .thenReturn(Optional.of(job));

        service.tryExecuteNextJob();

        verify(symbolReferenceRepository).deleteUnpublishedByScanJob(11L);
        verify(symbolRepository).deleteUnpublishedByScanJob(11L);
        verify(documentRepository).deleteUnpublishedByScanJob(11L);
        verify(symbolRepository, never()).publishByScanJob(anyLong());
        verify(symbolReferenceRepository, never()).publishByScanJob(anyLong());
        verify(documentRepository, never()).publishByScanJob(anyLong());
    }

    @Test
    void tryExecuteNextJob_still_returns_true_even_on_failure() {
        var job = queuedJob(12L, 77L);

        when(scanJobRepository.pollNextQueued()).thenReturn(Optional.of(job));
        when(repositoryStore.findByIdentifier(new RepositoryIdentifier(77L)))
                .thenThrow(new RuntimeException("DB error"));
        doNothing().when(scanJobRepository).update(any());
        when(scanJobRepository.findByIdentifier(new ScanJobIdentifier(12L)))
                .thenReturn(Optional.of(job));

        boolean result = service.tryExecuteNextJob();

        assertThat(result).isTrue();
        assertThat(job.status()).isEqualTo(ScanJob.ScanJobStatus.FAILED);
    }

    // ── pollAndMarkRunning ────────────────────────────────────────────────────

    @Test
    void pollAndMarkRunning_sets_status_to_running_and_records_start_time() {
        var job = queuedJob(20L, 1L);
        when(scanJobRepository.pollNextQueued()).thenReturn(Optional.of(job));
        doNothing().when(scanJobRepository).update(any());

        Optional<ScanJob> result = service.pollAndMarkRunning();

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(ScanJob.ScanJobStatus.RUNNING);
        assertThat(result.get().startedAt()).isPresent();
        verify(scanJobRepository).update(job);
    }

    @Test
    void pollAndMarkRunning_returns_empty_when_no_job_queued() {
        when(scanJobRepository.pollNextQueued()).thenReturn(Optional.empty());

        Optional<ScanJob> result = service.pollAndMarkRunning();

        assertThat(result).isEmpty();
        verify(scanJobRepository, never()).update(any());
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
