package com.hlag.sourceviewer.application.scan;

import com.hlag.sourceviewer.application.scan.indexer.LanguageIndexerRegistry;
import com.hlag.sourceviewer.application.scan.indexer.SelectedIndexerContext;
import com.hlag.sourceviewer.domain.model.identifier.BranchName;
import com.hlag.sourceviewer.domain.model.identifier.CommitSha;
import com.hlag.sourceviewer.domain.model.identifier.QualifiedName;
import com.hlag.sourceviewer.domain.model.identifier.DisplayName;
import com.hlag.sourceviewer.domain.model.identifier.ErrorMessage;
import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import com.hlag.sourceviewer.domain.model.identifier.ScanJobIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.SimpleName;
import com.hlag.sourceviewer.domain.model.identifier.SymbolIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.TokenCount;
import com.hlag.sourceviewer.domain.model.repository.ContentSha;
import com.hlag.sourceviewer.domain.model.repository.Repository;
import com.hlag.sourceviewer.domain.model.source.Document;
import com.hlag.sourceviewer.domain.model.source.ExtractedToken;
import com.hlag.sourceviewer.domain.model.source.ScanJob;
import com.hlag.sourceviewer.domain.model.source.SourceFile;
import com.hlag.sourceviewer.domain.model.source.Symbol;
import com.hlag.sourceviewer.domain.model.source.SymbolReference;
import com.hlag.sourceviewer.domain.port.incoming.ExecuteScanJobUseCase;
import com.hlag.sourceviewer.domain.port.incoming.ManageAppSettingsUseCase;
import com.hlag.sourceviewer.domain.port.outgoing.DocumentRepository;
import com.hlag.sourceviewer.domain.port.outgoing.GitAccess;
import com.hlag.sourceviewer.domain.port.outgoing.RepositoryStore;
import com.hlag.sourceviewer.domain.port.outgoing.ScanJobRepository;
import com.hlag.sourceviewer.domain.port.outgoing.SourceFileRepository;
import com.hlag.sourceviewer.domain.port.outgoing.SymbolReferenceRepository;
import com.hlag.sourceviewer.domain.port.outgoing.SymbolRepository;
import com.hlag.sourceviewer.domain.port.outgoing.TokenDetailRepository;
import com.hlag.sourceviewer.domain.port.outgoing.TokenStreamRepository;
import com.hlag.sourceviewer.domain.port.outgoing.TypeHierarchyRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.transaction.InvalidTransactionException;
import jakarta.transaction.NotSupportedException;
import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link ExecuteScanJobUseCase}.
 *
 * <p>Atomically claims the next queued scan job (using {@code FOR UPDATE SKIP LOCKED}),
 * executes it, and updates the status to {@code DONE} or {@code FAILED}.
 * Multi-instance safe: two application instances polling simultaneously will each
 * claim a different row because the poll and the RUNNING status update happen in
 * the same database transaction.</p>
 *
 * <p>Indexing uses a two-phase approach to avoid transaction timeouts on large repositories:
 * files are indexed in small batches (each in its own transaction) with {@code published=false},
 * then atomically activated in a single final transaction. This applies to documents, symbols,
 * and symbol references alike. Readers always filter {@code published=true} so they see either
 * all-old or all-new data, never a mix from partially-completed scans.</p>
 *
 * <p>The scan itself is also split into two phases: Phase 1 indexes all files for full-text
 * search; Phase 2 runs language-specific symbol indexing via {@link LanguageIndexerRegistry}.</p>
 */
@ApplicationScoped
public class ExecuteScanJobService implements ExecuteScanJobUseCase {

    private static final Logger logger = LoggerFactory.getLogger(ExecuteScanJobService.class);

    private final ScanJobRepository scanJobRepository;
    private final RepositoryStore repositoryStore;
    private final GitAccess gitAccess;
    private final SourceFileRepository sourceFileRepository;
    private final DocumentRepository documentRepository;
    private final TransactionManager transactionManager;
    private final SymbolRepository symbolRepository;
    private final SymbolReferenceRepository symbolReferenceRepository;
    private final ManageAppSettingsUseCase manageAppSettings;
    private final LanguageIndexerRegistry languageIndexerRegistry;
    private final TokenStreamRepository tokenStreamRepository;
    private final TokenDetailRepository tokenDetailRepository;
    private final TypeHierarchyRepository typeHierarchyRepository;

    @Inject
    public ExecuteScanJobService(
            ScanJobRepository scanJobRepository,
            RepositoryStore repositoryStore,
            GitAccess gitAccess,
            SourceFileRepository sourceFileRepository,
            DocumentRepository documentRepository,
            TransactionManager transactionManager,
            SymbolRepository symbolRepository,
            SymbolReferenceRepository symbolReferenceRepository,
            ManageAppSettingsUseCase manageAppSettings,
            LanguageIndexerRegistry languageIndexerRegistry,
            TokenStreamRepository tokenStreamRepository,
            TokenDetailRepository tokenDetailRepository,
            TypeHierarchyRepository typeHierarchyRepository) {
        this.scanJobRepository = scanJobRepository;
        this.repositoryStore = repositoryStore;
        this.gitAccess = gitAccess;
        this.sourceFileRepository = sourceFileRepository;
        this.documentRepository = documentRepository;
        this.transactionManager = transactionManager;
        this.symbolRepository = symbolRepository;
        this.symbolReferenceRepository = symbolReferenceRepository;
        this.manageAppSettings = manageAppSettings;
        this.languageIndexerRegistry = languageIndexerRegistry;
        this.tokenStreamRepository = tokenStreamRepository;
        this.tokenDetailRepository = tokenDetailRepository;
        this.typeHierarchyRepository = typeHierarchyRepository;
    }

    /**
     * Polls the next queued job and atomically marks it as {@code RUNNING} in a
     * dedicated new transaction. The transaction is managed directly via the injected
     * {@link TransactionManager} to keep this class testable without a CDI container.
     */
    Optional<ScanJob> pollAndMarkRunning() {
        AtomicReference<ScanJob> ref = new AtomicReference<>();
        runInNewTransaction(() ->
                scanJobRepository.pollNextQueued().ifPresent(job -> {
                    job.setStatus(ScanJob.ScanJobStatus.RUNNING);
                    job.setStartedAt(Instant.now());
                    job.setProgress(0);
                    scanJobRepository.update(job);
                    ref.set(job);
                    logger.info("Scan job {} claimed (repository {})",
                            job.identifier().value(),
                            job.repositoryIdentifier().value());
                }));
        return Optional.ofNullable(ref.get());
    }

    void markDone(ScanJobIdentifier identifier, int filesScanned) {
        runInNewTransaction(() ->
                scanJobRepository.findByIdentifier(identifier).ifPresent(job -> {
                    job.setStatus(ScanJob.ScanJobStatus.DONE);
                    job.setFinishedAt(Instant.now());
                    job.setFilesScanned(new TokenCount(filesScanned));
                    job.setProgress(100);
                    scanJobRepository.update(job);
                    logger.info("Scan job {} completed ({} files)", identifier.value(), filesScanned);
                }));
    }

    /**
     * Marks the scan job as FAILED in a dedicated new transaction.
     *
     * <p>Always runs in its own transaction so that the FAILED status is committed even
     * when a prior transaction has already been marked rollback-only (e.g. due to a
     * JTA timeout).</p>
     */
    void markFailed(ScanJobIdentifier identifier, String errorMessage) {
        try {
            runInNewTransaction(() ->
                    scanJobRepository.findByIdentifier(identifier).ifPresent(job -> {
                        job.setStatus(ScanJob.ScanJobStatus.FAILED);
                        job.setFinishedAt(Instant.now());
                        job.setErrorMessage(new ErrorMessage(errorMessage));
                        scanJobRepository.update(job);
                        logger.error("Scan job {} marked as failed: {}", identifier.value(), errorMessage);
                    }));
        } catch (Exception e) {
            logger.error("Could not persist FAILED status for scan job {} — job will remain stuck in RUNNING",
                    identifier.value(), e);
        }
    }

    /**
     * Deletes all unpublished documents, symbols, and references written by this scan job.
     * Called on failure to prevent partially-indexed content from lingering.
     * References are deleted before symbols to avoid unnecessary ON DELETE SET NULL cascades.
     */
    void cleanupOnFailure(ScanJobIdentifier identifier) {
        try {
            runInNewTransaction(() -> {
                Long scanJobId = identifier.value();
                symbolReferenceRepository.deleteUnpublishedByScanJob(scanJobId);
                symbolRepository.deleteUnpublishedByScanJob(scanJobId);
                documentRepository.deleteUnpublishedByScanJob(scanJobId);
                tokenStreamRepository.deleteUnpublishedByScanJob(scanJobId);
                tokenDetailRepository.deleteUnpublishedByScanJob(scanJobId);
                typeHierarchyRepository.deleteUnpublishedByScanJob(scanJobId);
            });
        } catch (Exception e) {
            logger.error("Failed to clean up unpublished data for scan job {}", identifier.value(), e);
        }
    }

    /**
     * Claims the next queued job and executes it using two-phase indexing.
     *
     * <p>Each step runs in its own transaction so no single transaction spans the full
     * repository scan. On failure, unpublished documents are cleaned up before the job
     * is marked FAILED.</p>
     */
    @Override
    @ActivateRequestContext
    public boolean tryExecuteNextJob() {
        Optional<ScanJob> job = pollAndMarkRunning();
        if (job.isEmpty()) {
            return false;
        }

        ScanJob scanJob = job.get();
        ScanJobIdentifier identifier = scanJob.identifier();

        try {
            Repository repository = repositoryStore.findByIdentifier(scanJob.repositoryIdentifier())
                    .orElseThrow(() -> new IllegalStateException(
                            "Repository not found for scan job " + identifier.value()));

            int filesScanned = performScan(scanJob, repository);
            markDone(identifier, filesScanned);
        } catch (Exception exception) {
            logger.error("Scan job {} failed with exception", identifier.value(), exception);
            cleanupOnFailure(identifier);
            markFailed(identifier, exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName());
        }

        return true;
    }

    private int performScan(ScanJob scanJob, Repository repository) {
        logger.info("Starting scan of repository '{}' for job {} (trigger: {}, commit: {}, force: {})",
                repository.name().value(),
                scanJob.identifier().value(),
                scanJob.triggerType(),
                scanJob.commitSha().map(CommitSha::value).orElse("HEAD"),
                scanJob.forceFullReindex());

        gitAccess.prepareRepository(repository);

        var targetSha = scanJob.commitSha()
                .orElseGet(() -> gitAccess.fetchRemoteHeadSha(repository, repository.defaultBranch()));
        var branchName = repository.defaultBranch();

        List<FilePath> toIndex;
        List<FilePath> toDelete;

        boolean isFirstScan = repository.lastCommitSha().isEmpty();
        if (scanJob.forceFullReindex() || isFirstScan) {
            logger.info("Full index of repository '{}' at commit {}", repository.name().value(), targetSha.shortForm());
            toIndex = gitAccess.listAllFiles(repository, targetSha);
            toDelete = List.of();
            // TODO: Even for a full scan it might be necessary to delete old files.
        } else {
            var lastSha = repository.lastCommitSha().get();
            logger.info("Incremental index of repository '{}': {} → {}",
                    repository.name().value(), lastSha.shortForm(), targetSha.shortForm());
            toIndex = gitAccess.changedFilesBetween(repository, lastSha, targetSha);
            toDelete = gitAccess.deletedFilesBetween(repository, lastSha, targetSha);
        }

        // TODO: delete files later, as the indexing might fail... then we would like to keep the previous version
        for (FilePath path : toDelete) {
            runInNewTransaction(() ->
                    sourceFileRepository.findByRepositoryAndPath(repository.identifier(), branchName, path)
                            .ifPresent(f -> {
                                logger.debug("Removing deleted file from index: {}", path.value());
                                sourceFileRepository.deleteByIdentifier(f.identifier());
                            }));
        }

        // ── Phase 1: full-text-search document indexing ───────────────────────
        int indexed = runBatchLoop(scanJob.identifier(), toIndex, repository.name().value(), "document",
                batch -> indexDocumentBatch(batch, scanJob, repository, targetSha, branchName));

        // ── Phase 2: language-specific symbol indexing ────────────────────────
        Path repoLocalPath = gitAccess.getLocalPath(repository);
        Map<String, SelectedIndexerContext> indexerContexts =
                languageIndexerRegistry.selectAndPrepare(repoLocalPath, toIndex, repository);

        try {
            if (!indexerContexts.isEmpty()) {
                runBatchLoop(scanJob.identifier(), toIndex, repository.name().value(), "symbol",
                        false,  // collection is slow; indexSymbolBatch owns its storage transaction
                        batch -> indexSymbolBatch(batch, scanJob, repository, targetSha, indexerContexts));
            } else {
                logger.info("No symbol indexers available for repository '{}'", repository.name().value());
            }
        } finally {
            indexerContexts.values()
                    .forEach(indexerContext -> indexerContext
                            .indexer()
                            .teardown(indexerContext.context()));
        }

        runInNewTransaction(() ->
                activateDocuments(scanJob.identifier(), repository, targetSha));

        logger.info("Scan of repository '{}' for job {} completed: {} indexed, {} deleted",
                repository.name().value(), scanJob.identifier().value(), indexed, toDelete.size());

        return indexed;
    }

    private record CollectedFile(FileIdentifier fileId, ParsedFile parsedFile) {}

    /**
     * Runs a batch loop over {@code files} with adaptive batch-size halving on timeout.
     * Returns the total count returned by {@code batchAction} (meaningful for Phase 1;
     * Phase 2 can ignore the return value).
     *
     * <p>When {@code wrapInTransaction} is {@code true} (the default), each batch is executed
     * inside a new JTA transaction. When {@code false}, the batch action is responsible for
     * managing its own transaction(s) — use this when the action performs expensive non-DB
     * work before storing, so that the transaction does not time out during collection.</p>
     */
    private int runBatchLoop(ScanJobIdentifier jobIdentifier, List<FilePath> files,
                             String repositoryName, String phase, boolean wrapInTransaction,
                             BatchAction batchAction) {
        int currentBatchSize = Integer.parseInt(manageAppSettings.getSetting(
                ManageAppSettingsUseCase.SETTING_SCAN_BATCH_SIZE,
                ManageAppSettingsUseCase.DEFAULT_SCAN_BATCH_SIZE));
        int numberOfProcessedFiles = 0;
        int indexInFiles = 0;
        while (indexInFiles < files.size()) {
            List<FilePath> batch = files.subList(indexInFiles, Math.min(indexInFiles + currentBatchSize, files.size()));
            AtomicInteger batchCount = new AtomicInteger(0);
            try {
                if (wrapInTransaction) {
                    runInNewTransaction(() -> batchCount.set(batchAction.run(batch)));
                } else {
                    batchCount.set(batchAction.run(batch));
                }
                numberOfProcessedFiles += batchCount.get();
                indexInFiles += batch.size();
                int progressPercent = files.isEmpty() ? 100 : numberOfProcessedFiles * 100 / files.size();
                logger.info("[{}] {}: {}/{} files done ({}%)",
                        repositoryName, phase,
                        numberOfProcessedFiles, files.size(),
                        progressPercent);
                updateProgress(jobIdentifier, progressPercent);
            } catch (TransactionTimeoutException e) {
                if (batch.size() <= 1) {
                    throw e;
                }
                currentBatchSize = Math.max(1, currentBatchSize / 2);
                logger.warn("Batch of {} files timed out for repository '{}' ({} phase) — reducing batch size to {} and retrying",
                        batch.size(), repositoryName, phase, currentBatchSize);
            } catch (RuntimeException e) {
                logger.error("Batch of {} files failed for repository '{}' ({} phase) — skipping batch",
                        batch.size(), repositoryName, phase, e);
                indexInFiles += batch.size();
            }
        }
        return numberOfProcessedFiles;
    }

    private int runBatchLoop(ScanJobIdentifier jobIdentifier, List<FilePath> files,
                             String repositoryName, String phase, BatchAction batchAction) {
        return runBatchLoop(jobIdentifier, files, repositoryName, phase, true, batchAction);
    }

    private int indexDocumentBatch(List<FilePath> filePaths, ScanJob scanJob, Repository repository,
                                   CommitSha targetSha, BranchName branchName) {
        final var maxChunkSize = Integer.parseInt(manageAppSettings.getSetting(
                ManageAppSettingsUseCase.SETTING_SCAN_CHUNK_SIZE,
                ManageAppSettingsUseCase.DEFAULT_SCAN_CHUNK_SIZE));
        var numberOfWorkedFiles = 0;
        for (final var path : filePaths) {
            try {
                final var contentOpt = gitAccess.readFileContent(repository, path, targetSha);
                if (contentOpt.isEmpty()) {
                    // TODO: The binary check should happen here, not in the git-access
                    logger.debug("Skipping binary file: {}", path.value());
                    continue;
                }
                final var content = contentOpt.get();
                final var contentSha = new ContentSha(sha256(content));
                if (content.isBlank()) {
                    logger.debug("Skipping empty file: {}", path.value());
                    continue;
                }

                logger.debug("Indexing for full-text search: {}", path.value());

                final var fileId = sourceFileRepository
                        .findByRepositoryAndPath(repository.identifier(), branchName, path)
                        .map(f -> {
                            sourceFileRepository.updateContentSha(f.identifier(), contentSha);
                            return f.identifier();
                        })
                        .orElseGet(() -> sourceFileRepository.insert(
                                new SourceFile(
                                        repository.identifier(),
                                        branchName,
                                        path,
                                        contentSha,
                                        // TODO: find language
                                        new DisplayName("unknown"),
                                        Instant.now())));

                final var chunks = splitIntoChunks(content, maxChunkSize);
                for (var chunk : chunks) {
                    documentRepository.insertUnpublished(
                            new Document(fileId, "source", chunk, scanJob.identifier().value()));
                }
                ++numberOfWorkedFiles;
            } catch (Exception e) {
                if (isTransactionRollbackPending()) {
                    throw new TransactionTimeoutException("Scan aborted: transaction timeout after " + numberOfWorkedFiles + " files", e);
                }
                logger.warn("Failed to index file '{}' in repository '{}': {}",
                        path.value(), repository.name().value(), e.getMessage());
            }
        }
        return numberOfWorkedFiles;
    }

    private int indexSymbolBatch(List<FilePath> filePaths, ScanJob scanJob, Repository repository,
                                 CommitSha targetSha,
                                 Map<String, SelectedIndexerContext> indexerContexts) {
        var branch = repository.defaultBranch();
        var scanJobId = scanJob.identifier().value();

        // ── Phase 1: collection (no transaction — parsing can be very slow) ──
        List<CollectedFile> collected = new ArrayList<>();
        for (var filePath : filePaths) {
            try {
                var indexerContext = indexerContexts.values().stream()
                        .filter(ctx -> ctx.handles(filePath))
                        .findFirst();
                if (indexerContext.isEmpty()) {
                    continue;
                }
                var contentOpt = gitAccess.readFileContent(repository, filePath, targetSha);
                if (contentOpt.isEmpty()) {
                    continue;
                }
                var sourceFileOpt = sourceFileRepository.findByRepositoryAndPath(
                        repository.identifier(), branch, filePath);
                if (sourceFileOpt.isEmpty()) {
                    continue;
                }
                logger.debug("Indexing symbols of {}: {}", indexerContext.get().indexer().supportedLanguage(), filePath.value());
                var fileId = sourceFileOpt.get().identifier();
                var parsedFile = indexerContext.get().index(fileId, filePath, contentOpt.get());
                collected.add(new CollectedFile(fileId, parsedFile));
            } catch (Exception e) {
                logger.warn("Failed to collect symbols for '{}' in repository '{}': {}",
                        filePath.value(), repository.name().value(), e.getMessage());
            }
        }

        // ── Phase 2: storage (inside one new transaction — fast DB inserts only) ──
        if (!collected.isEmpty()) {
            AtomicInteger stored = new AtomicInteger(0);
            runInNewTransaction(() -> {
                for (var r : collected) {
                    try {
                        storeSymbols(r.parsedFile(), r.fileId(), scanJobId, repository, branch);
                        storeTokenStream(r.parsedFile(), r.fileId(), scanJobId);
                        stored.incrementAndGet();
                    } catch (Exception e) {
                        if (isTransactionRollbackPending()) {
                            throw new TransactionTimeoutException(
                                    "Symbol storage aborted: transaction timeout after " + stored.get() + " files", e);
                        }
                        logger.warn("Failed to store symbols for file in repository '{}': {}",
                                repository.name().value(), e.getMessage());
                    }
                }
            });
        }
        return collected.size();
    }

    private void storeSymbols(ParsedFile parsed, FileIdentifier fileId, Long scanJobId,
                               Repository repository, BranchName branch) {
        if (!parsed.tokenDetails().isEmpty()) {
            tokenDetailRepository.insertAllUnpublished(parsed.tokenDetails(), scanJobId);
        }
        if (!parsed.hierarchyEntries().isEmpty()) {
            typeHierarchyRepository.insertAllUnpublished(parsed.hierarchyEntries(), scanJobId);
        }

        parsed.declarations().forEach(symbol -> symbolRepository.insertUnpublished(symbol, scanJobId));

        for (var ref : parsed.references()) {
            // 1. Try FQN-based resolution (fast path).
            Optional<Symbol> resolvedSym = ref.resolvedName()
                    .flatMap(qn -> symbolRepository.findByQualifiedNameForScan(qn, scanJobId));

            // 2. Fall back to definition-location-based resolution (LSP definition result).
            if (resolvedSym.isEmpty()
                    && ref.definitionFilePath().isPresent()
                    && ref.definitionLine().isPresent()
                    && ref.definitionColumn().isPresent()) {
                var defFileOpt = sourceFileRepository.findByRepositoryAndPath(
                        repository.identifier(), branch, ref.definitionFilePath().get());
                if (defFileOpt.isPresent()) {
                    resolvedSym = symbolRepository.findByFileAndPositionForScan(
                            defFileOpt.get().identifier(),
                            ref.definitionLine().get().value(),
                            ref.definitionColumn().get().value(),
                            scanJobId);
                }
            }

            Optional<SymbolIdentifier> symId = resolvedSym.map(Symbol::identifier);
            // Always store the FQN — either from the resolved symbol or from resolvedName.
            Optional<QualifiedName> fqnToStore = resolvedSym.map(Symbol::qualifiedName)
                    .or(ref::resolvedName);
            Optional<SimpleName> nameToStore = symId.isPresent()
                    ? Optional.empty()
                    : ref.resolvedName().map(qn -> new SimpleName(qn.value()))
                            .or(ref::unresolvedName);

            symbolReferenceRepository.insertUnpublished(
                    new SymbolReference(fileId, symId, fqnToStore, nameToStore,
                            ref.kind(), ref.line(), ref.column()),
                    scanJobId);
        }
    }

    private void storeTokenStream(ParsedFile parsed, FileIdentifier fileId, Long scanJobId) {
        if (parsed.tokens().isEmpty()) {
            return;
        }

        // Build lookup: "line:col" → highlight group ID, from pre-computed groups.
        Map<String, Integer> highlightGroups = parsed.highlightGroups();

        // Build set of positions that have a token_detail entry (= clickable tokens).
        java.util.Set<String> detailPositions = new java.util.HashSet<>();
        for (com.hlag.sourceviewer.domain.model.source.TokenDetail td : parsed.tokenDetails()) {
            detailPositions.add(td.line() + ":" + td.columnStart());
        }

        // Enrich tokens with highlight group IDs and hasDetails flag.
        var enriched = new ArrayList<ExtractedToken>(parsed.tokens().size());
        for (ExtractedToken token : parsed.tokens()) {
            String key = token.line() + ":" + token.columnStart();
            Integer hg = highlightGroups.get(key);
            boolean hasDetails = detailPositions.contains(key);
            if (hg != null || hasDetails) {
                enriched.add(token.withHighlightGroupId(hg).withHasDetails(hasDetails));
            } else {
                enriched.add(token);
            }
        }

        tokenStreamRepository.storeUnpublished(fileId, enriched, scanJobId);
    }

    private void activateDocuments(ScanJobIdentifier identifier, Repository repository, CommitSha targetSha) {
        Long scanJobId = identifier.value();
        symbolRepository.publishByScanJob(scanJobId);
        symbolReferenceRepository.publishByScanJob(scanJobId);
        symbolRepository.deleteSupersededByScanJob(scanJobId);
        symbolReferenceRepository.deleteSupersededByScanJob(scanJobId);
        documentRepository.publishByScanJob(scanJobId);
        documentRepository.deleteSupersededDocuments(scanJobId);
        tokenStreamRepository.publishByScanJob(scanJobId);
        tokenStreamRepository.deleteSupersededByScanJob(scanJobId);
        tokenDetailRepository.publishByScanJob(scanJobId);
        tokenDetailRepository.deleteSupersededByScanJob(scanJobId);
        typeHierarchyRepository.publishByScanJob(scanJobId);
        typeHierarchyRepository.deleteSupersededByScanJob(scanJobId);
        repository.setLastCommitSha(targetSha);
        repository.setLastScannedAt(Instant.now());
        repositoryStore.update(repository);
    }

    @Override
    public int recoverStaleJobs(Instant staleBefore) {
        List<ScanJob> stale = scanJobRepository.findStaleRunningJobs(staleBefore);
        for (ScanJob job : stale) {
            ScanJobIdentifier id = job.identifier();
            logger.warn("Recovering stale scan job {} (repository {}, last heartbeat {})",
                    id.value(), job.repositoryIdentifier().value(),
                    job.lastHeartbeatAt().map(Instant::toString).orElse("never"));
            cleanupOnFailure(id);
            markFailed(id, "Scan job timed out — no heartbeat detected, likely killed mid-run");
        }
        return stale.size();
    }

    private void updateProgress(ScanJobIdentifier identifier, int progress) {
        try {
            runInNewTransaction(() ->
                    scanJobRepository.findByIdentifier(identifier).ifPresent(job -> {
                        job.setLastHeartbeatAt(Instant.now());
                        job.setProgress(progress);
                        scanJobRepository.update(job);
                    }));
        } catch (Exception e) {
            logger.warn("Failed to update heartbeat/progress for scan job {}", identifier.value(), e);
        }
    }

    /**
     * Runs the given action in a new JTA transaction, suspending any current transaction
     * for the duration. Uses the injected {@link TransactionManager} directly so the service
     * remains testable without a CDI container (tests supply a mock TM).
     */
    private void runInNewTransaction(Runnable action) {
        Transaction suspended = null;
        try {
            suspended = transactionManager.suspend();
            transactionManager.begin();
        } catch (NotSupportedException | SystemException e) {
            throw new IllegalStateException("Cannot begin new transaction", e);
        }
        Throwable failure = null;
        try {
            action.run();
            transactionManager.commit();
        } catch (Throwable t) {
            failure = t;
            try {
                transactionManager.rollback();
            } catch (Exception re) {
                logger.warn("Rollback failed after transaction error", re);
            }
        } finally {
            if (suspended != null) {
                try {
                    transactionManager.resume(suspended);
                } catch (InvalidTransactionException | SystemException e) {
                    logger.warn("Failed to resume suspended transaction", e);
                }
            }
        }
        if (failure instanceof RuntimeException re) throw re;
        if (failure != null) throw new RuntimeException(failure);
    }

    private boolean isTransactionRollbackPending() {
        try {
            int status = transactionManager.getStatus();
            return status == Status.STATUS_MARKED_ROLLBACK || status == Status.STATUS_ROLLEDBACK;
        } catch (SystemException e) {
            return false;
        }
    }

    /**
     * Splits {@code content} into chunks of at most {@code maxChunkSize} characters,
     * breaking only at newline boundaries. A single line longer than the limit is
     * kept as one chunk. Returns a single-element list when the content already fits.
     */
    static List<String> splitIntoChunks(String content, int maxChunkSize) {
        if (maxChunkSize <= 0 || content.length() <= maxChunkSize) {
            return List.of(content);
        }
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int start = 0;
        while (start < content.length()) {
            int newline = content.indexOf('\n', start);
            int end = (newline == -1) ? content.length() : newline + 1;
            String line = content.substring(start, end);
            if (line.length() > maxChunkSize) {
                // Line exceeds the limit — flush current chunk then split the line at character boundaries
                if (!current.isEmpty()) {
                    chunks.add(current.toString());
                    current.setLength(0);
                }
                for (int i = 0; i < line.length(); i += maxChunkSize) {
                    chunks.add(line.substring(i, Math.min(i + maxChunkSize, line.length())));
                }
            } else {
                if (current.length() + line.length() > maxChunkSize) {
                    chunks.add(current.toString());
                    current.setLength(0);
                }
                current.append(line);
            }
            start = end;
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }
        return chunks;
    }

    private static String sha256(String content) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
