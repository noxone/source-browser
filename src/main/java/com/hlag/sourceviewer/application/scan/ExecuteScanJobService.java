package com.hlag.sourceviewer.application.scan;

import com.hlag.sourceviewer.application.scan.indexer.LanguageIndexerRegistry;
import com.hlag.sourceviewer.application.scan.indexer.SelectedIndexerContext;
import com.hlag.sourceviewer.domain.model.identifier.BranchName;
import com.hlag.sourceviewer.domain.model.identifier.CommitSha;
import com.hlag.sourceviewer.domain.model.identifier.DisplayName;
import com.hlag.sourceviewer.domain.model.identifier.ErrorMessage;
import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import com.hlag.sourceviewer.domain.model.identifier.QualifiedName;
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
import com.hlag.sourceviewer.domain.port.outgoing.SymbolRepository;
import com.hlag.sourceviewer.domain.port.outgoing.SymbolReferenceRepository;
import com.hlag.sourceviewer.domain.port.outgoing.TokenStreamRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.InvalidTransactionException;
import jakarta.transaction.NotSupportedException;
import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
            TokenStreamRepository tokenStreamRepository) {
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

    private int performScan(ScanJob job, Repository repository) {
        logger.info("Starting scan of repository '{}' for job {} (trigger: {}, commit: {}, force: {})",
                repository.name().value(),
                job.identifier().value(),
                job.triggerType(),
                job.commitSha().map(CommitSha::value).orElse("HEAD"),
                job.forceFullReindex());

        gitAccess.prepareRepository(repository);

        var targetSha = job.commitSha()
                .orElseGet(() -> gitAccess.fetchRemoteHeadSha(repository, repository.defaultBranch()));
        var branch = repository.defaultBranch();

        List<FilePath> toIndex;
        List<FilePath> toDelete;

        boolean isFirstScan = repository.lastCommitSha().isEmpty();
        if (job.forceFullReindex() || isFirstScan) {
            logger.info("Full index of repository '{}' at commit {}", repository.name().value(), targetSha.shortForm());
            toIndex = gitAccess.listAllFiles(repository, targetSha);
            toDelete = List.of();
        } else {
            var lastSha = repository.lastCommitSha().get();
            logger.info("Incremental index of repository '{}': {} → {}",
                    repository.name().value(), lastSha.shortForm(), targetSha.shortForm());
            toIndex = gitAccess.changedFilesBetween(repository, lastSha, targetSha);
            toDelete = gitAccess.deletedFilesBetween(repository, lastSha, targetSha);
        }

        for (FilePath path : toDelete) {
            runInNewTransaction(() ->
                    sourceFileRepository.findByRepositoryAndPath(repository.identifier(), branch, path)
                            .ifPresent(f -> {
                                logger.debug("Removing deleted file from index: {}", path.value());
                                sourceFileRepository.deleteByIdentifier(f.identifier());
                            }));
        }

        // ── Phase 1: full-text-search document indexing ───────────────────────
        int indexed = runBatchLoop(toIndex, repository.name().value(), "document",
                batch -> indexDocumentBatch(batch, job, repository, targetSha, branch));

        // ── Phase 2: language-specific symbol indexing ────────────────────────
        Path repoLocalPath = gitAccess.getLocalPath(repository);
        Map<String, SelectedIndexerContext> indexerContexts =
                languageIndexerRegistry.selectAndPrepare(repoLocalPath, toIndex);

        if (!indexerContexts.isEmpty()) {
            runBatchLoop(toIndex, repository.name().value(), "symbol",
                    batch -> indexSymbolBatch(batch, job, repository, targetSha, indexerContexts));
        }

        runInNewTransaction(() ->
                activateDocuments(job.identifier(), repository, targetSha));

        logger.info("Scan of repository '{}' for job {} completed: {} indexed, {} deleted",
                repository.name().value(), job.identifier().value(), indexed, toDelete.size());

        return indexed;
    }

    /**
     * Runs a batch loop over {@code files} with adaptive batch-size halving on timeout.
     * Returns the total count returned by {@code batchAction} (meaningful for Phase 1;
     * Phase 2 can ignore the return value).
     */
    private int runBatchLoop(List<FilePath> files, String repoName, String phase,
                             BatchAction batchAction) {
        int currentBatchSize = Integer.parseInt(manageAppSettings.getSetting(
                ManageAppSettingsUseCase.SETTING_SCAN_BATCH_SIZE,
                ManageAppSettingsUseCase.DEFAULT_SCAN_BATCH_SIZE));
        int total = 0;
        int i = 0;
        while (i < files.size()) {
            List<FilePath> batch = files.subList(i, Math.min(i + currentBatchSize, files.size()));
            AtomicInteger batchCount = new AtomicInteger(0);
            try {
                runInNewTransaction(() -> batchCount.set(batchAction.run(batch)));
                total += batchCount.get();
                i += batch.size();
            } catch (RuntimeException e) {
                if (batch.size() <= 1) {
                    throw e;
                }
                currentBatchSize = Math.max(1, currentBatchSize / 2);
                logger.warn("Batch of {} files timed out for repository '{}' ({} phase) — reducing batch size to {} and retrying",
                        batch.size(), repoName, phase, currentBatchSize);
            }
        }
        return total;
    }

    @FunctionalInterface
    private interface BatchAction {
        int run(List<FilePath> batch);
    }

    private int indexDocumentBatch(List<FilePath> batch, ScanJob job, Repository repository,
                                   CommitSha targetSha, BranchName branch) {
        int indexed = 0;
        for (FilePath path : batch) {
            try {
                var contentOpt = gitAccess.readFileContent(repository, path, targetSha);
                if (contentOpt.isEmpty()) {
                    logger.debug("Skipping binary file: {}", path.value());
                    continue;
                }
                logger.debug("Indexing for full-text search: {}", path.value());
                String content = contentOpt.get();
                var contentSha = new ContentSha(sha256(content));

                var existing = sourceFileRepository.findByRepositoryAndPath(repository.identifier(), branch, path);
                var fileId = existing
                        .map(f -> {
                            sourceFileRepository.updateContentSha(f.identifier(), contentSha);
                            return f.identifier();
                        })
                        .orElseGet(() -> sourceFileRepository.insert(
                                new SourceFile(
                                        repository.identifier(),
                                        branch,
                                        path,
                                        contentSha,
                                        new DisplayName("unknown"),
                                        Instant.now())));

                documentRepository.insertUnpublished(new Document(fileId, "source", content, job.identifier().value()));
                indexed++;
            } catch (Exception e) {
                if (isTransactionRollbackPending()) {
                    logger.error("Exception happened while indexing '{}' in '{}' after {} files — aborting batch",
                            path.value(), repository.name().value(), indexed, e);
                    throw new RuntimeException("Scan aborted: transaction timeout after " + indexed + " files", e);
                }
                logger.warn("Failed to index file '{}' in repository '{}': {}",
                        path.value(), repository.name().value(), e.getMessage());
            }
        }
        return indexed;
    }

    private int indexSymbolBatch(List<FilePath> batch, ScanJob job, Repository repository,
                                 CommitSha targetSha,
                                 Map<String, SelectedIndexerContext> indexerContexts) {
        var branch = repository.defaultBranch();
        int indexed = 0;
        for (FilePath path : batch) {
            try {
                var matchingContext = indexerContexts.values().stream()
                        .filter(ctx -> ctx.handles(path))
                        .findFirst();
                if (matchingContext.isEmpty()) {
                    continue;
                }
                var contentOpt = gitAccess.readFileContent(repository, path, targetSha);
                if (contentOpt.isEmpty()) {
                    continue;
                }
                var fileOpt = sourceFileRepository.findByRepositoryAndPath(
                        repository.identifier(), branch, path);
                if (fileOpt.isEmpty()) {
                    continue;
                }
                logger.debug("Indexing symbols of {}: {}", matchingContext.get().indexer().supportedLanguage(), path.value());
                var fileId = fileOpt.get().identifier();
                Long scanJobId = job.identifier().value();
                var parsed = matchingContext.get().index(fileId, path, contentOpt.get());
                storeSymbols(parsed, fileId, scanJobId);
                storeTokenStream(parsed, fileId, scanJobId);
                indexed++;
            } catch (Exception e) {
                if (isTransactionRollbackPending()) {
                    logger.error("Exception happened while indexing symbols for '{}' in '{}' after {} files — aborting batch",
                            path.value(), repository.name().value(), indexed, e);
                    throw new RuntimeException("Symbol indexing aborted: transaction timeout after " + indexed + " files", e);
                }
                logger.warn("Failed to index symbols for '{}' in repository '{}': {}",
                        path.value(), repository.name().value(), e.getMessage());
            }
        }
        return indexed;
    }

    private void storeSymbols(JavaFileParser.ParsedFile parsed, FileIdentifier fileId, Long scanJobId) {
        parsed.declarations().forEach(symbol -> symbolRepository.insertUnpublished(symbol, scanJobId));

        for (var ref : parsed.references()) {
            // Prefer the newly inserted (unpublished) symbol for this scan job so that the
            // reference survives activation with the correct new symbol ID.
            Optional<SymbolIdentifier> symId = ref.resolvedName()
                    .flatMap(qn -> symbolRepository.findByQualifiedNameForScan(qn, scanJobId))
                    .map(Symbol::identifier);

            Optional<SimpleName> nameToStore = symId.isPresent()
                    ? Optional.empty()
                    : ref.resolvedName().map(qn -> new SimpleName(qn.value()))
                            .or(ref::unresolvedName);

            symbolReferenceRepository.insertUnpublished(
                    new SymbolReference(fileId, symId, nameToStore, ref.kind(), ref.line(), ref.column()),
                    scanJobId);
        }
    }

    private void storeTokenStream(JavaFileParser.ParsedFile parsed, FileIdentifier fileId, Long scanJobId) {
        if (parsed.tokens().isEmpty()) {
            return;
        }

        // Build lookup: "line:col" → qualified name + symbol ID, from already-persisted declarations.
        // Symbol IDs are available because insertUnpublished flushes the IDENTITY-generated ID back.
        Map<String, String> declarationQn = new HashMap<>();
        Map<String, Long> declarationId = new HashMap<>();
        for (Symbol sym : parsed.declarations()) {
            sym.lineStart().ifPresent(line ->
                sym.columnStart().ifPresent(col -> {
                    String key = line.value() + ":" + col.value();
                    declarationQn.put(key, sym.qualifiedName().value());
                    if (sym.identifier() != null) {
                        declarationId.put(key, sym.identifier().value());
                    }
                }));
        }

        // Build lookup from unpublished references for this scan job (fallback: published).
        Map<String, SymbolReference> refMap = new HashMap<>();
        for (SymbolReference ref : symbolReferenceRepository.findByFileForScan(fileId, scanJobId)) {
            ref.line().ifPresent(line ->
                ref.columnStart().ifPresent(col ->
                    refMap.put(line.value() + ":" + col.value(), ref)));
        }

        // Enrich IDENTIFIER tokens with semantic information where available.
        var enriched = new ArrayList<ExtractedToken>(parsed.tokens().size());
        for (ExtractedToken token : parsed.tokens()) {
            if (token.kind() != ExtractedToken.TokenKind.IDENTIFIER) {
                enriched.add(token);
                continue;
            }
            String key = token.line() + ":" + token.columnStart();
            String qn = declarationQn.get(key);
            Long symId = declarationId.get(key);
            if (qn == null) {
                SymbolReference ref = refMap.get(key);
                if (ref != null) {
                    symId = ref.symbolIdentifier().map(SymbolIdentifier::value).orElse(null);
                }
            }
            enriched.add(new ExtractedToken(
                    token.line(), token.columnStart(), token.columnEnd(),
                    token.text(), token.kind(), qn, symId));
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
        repository.setLastCommitSha(targetSha);
        repository.setLastScannedAt(Instant.now());
        repositoryStore.update(repository);
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
