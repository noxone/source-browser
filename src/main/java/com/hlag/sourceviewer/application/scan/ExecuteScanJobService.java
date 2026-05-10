package com.hlag.sourceviewer.application.scan;

import com.hlag.sourceviewer.domain.model.identifier.ErrorMessage;
import com.hlag.sourceviewer.domain.model.identifier.ScanJobIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.TokenCount;
import com.hlag.sourceviewer.domain.model.repository.Repository;
import com.hlag.sourceviewer.domain.model.source.ScanJob;
import com.hlag.sourceviewer.domain.port.incoming.ExecuteScanJobUseCase;
import com.hlag.sourceviewer.domain.port.outgoing.GitAccess;
import com.hlag.sourceviewer.domain.port.outgoing.RepositoryStore;
import com.hlag.sourceviewer.domain.port.outgoing.ScanJobRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;

/**
 * Implementation of {@link ExecuteScanJobUseCase}.
 *
 * <p>Atomically claims the next queued scan job (using {@code FOR UPDATE SKIP LOCKED}),
 * executes it, and updates the status to {@code DONE} or {@code FAILED}.
 * Multi-instance safe: two application instances polling simultaneously will each
 * claim a different row because the poll and the RUNNING status update happen in
 * the same database transaction.</p>
 */
@ApplicationScoped
public class ExecuteScanJobService implements ExecuteScanJobUseCase {

    private static final Logger logger = LoggerFactory.getLogger(ExecuteScanJobService.class);

    private final ScanJobRepository scanJobRepository;
    private final RepositoryStore repositoryStore;
    private final GitAccess gitAccess;

    @Inject
    public ExecuteScanJobService(
            ScanJobRepository scanJobRepository,
            RepositoryStore repositoryStore,
            GitAccess gitAccess) {
        this.scanJobRepository = scanJobRepository;
        this.repositoryStore = repositoryStore;
        this.gitAccess = gitAccess;
    }

    /**
     * Polls the next queued job and atomically marks it as {@code RUNNING} in the
     * same transaction.  The {@code FOR UPDATE SKIP LOCKED} inside
     * {@link ScanJobRepository#pollNextQueued()} participates in this outer transaction,
     * so no other instance can claim the same row.
     */
    @Transactional
    Optional<ScanJob> pollAndMarkRunning() {
        return scanJobRepository.pollNextQueued().map(job -> {
            job.setStatus(ScanJob.ScanJobStatus.RUNNING);
            job.setStartedAt(Instant.now());
            scanJobRepository.update(job);
            logger.info("Scan job {} claimed (repository {})",
                    job.identifier().value(),
                    job.repositoryIdentifier().value());
            return job;
        });
    }

    @Transactional
    void markDone(ScanJobIdentifier identifier, int filesScanned) {
        scanJobRepository.findByIdentifier(identifier).ifPresent(job -> {
            job.setStatus(ScanJob.ScanJobStatus.DONE);
            job.setFinishedAt(Instant.now());
            job.setFilesScanned(new TokenCount(filesScanned));
            scanJobRepository.update(job);
            logger.info("Scan job {} completed ({} files)", identifier.value(), filesScanned);
        });
    }

    @Transactional
    void markFailed(ScanJobIdentifier identifier, String errorMessage) {
        scanJobRepository.findByIdentifier(identifier).ifPresent(job -> {
            job.setStatus(ScanJob.ScanJobStatus.FAILED);
            job.setFinishedAt(Instant.now());
            job.setErrorMessage(new ErrorMessage(errorMessage));
            scanJobRepository.update(job);
            logger.error("Scan job {} failed: {}", identifier.value(), errorMessage);
        });
    }

    /**
     * Atomically claims the next queued job and executes it.
     *
     * <p>Must be {@code @Transactional} so that all database operations within this method
     * (including self-invoked helpers like {@link #pollAndMarkRunning()}) run within an
     * active JTA transaction — required by Hibernate even when sub-methods are annotated
     * individually, because self-invocation bypasses the CDI proxy and its interceptors.</p>
     *
     * <p><b>Note:</b> for the current stub implementation this is acceptable. When
     * {@link #performScan} is replaced with a real (potentially long-running) scan, the
     * method should be refactored to commit the RUNNING status in its own short transaction
     * before starting the scan, then commit DONE/FAILED in a second transaction.</p>
     */
    @Override
    @Transactional
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
            markFailed(identifier, exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName());
        }

        return true;
    }

    /**
     * Performs the actual repository scan.
     *
     * <p>This is a stub implementation. The real implementation should:</p>
     * <ol>
     *   <li>Determine which files changed since the last scan using
     *       {@code GitAccess.changedFilesBetween()} (or do a full scan for the
     *       first-ever scan).</li>
     *   <li>For each changed file, read the content via
     *       {@code GitAccess.readFileContent()}.</li>
     *   <li>Parse each file and extract symbols (classes, methods, fields, etc.).</li>
     *   <li>Upsert {@code SourceFile} and {@code Symbol} records via their
     *       respective repositories.</li>
     *   <li>Update {@code Repository.lastCommitSha} and {@code Repository.lastScannedAt}
     *       via {@code RepositoryStore.update()}.</li>
     * </ol>
     *
     * @param job        the scan job being executed
     * @param repository the repository to scan
     * @return the number of files scanned
     */
    private int performScan(ScanJob job, Repository repository) {
        logger.info("Starting scan of repository '{}' for job {} (trigger: {}, commit: {})",
                repository.name().value(),
                job.identifier().value(),
                job.triggerType(),
                job.commitSha().map(sha -> sha.value()).orElse("HEAD"));

        gitAccess.prepareRepository(repository);

        // TODO (1): Fetch the target commit SHA and determine changed files
        //   CommitSha targetSha = job.commitSha().orElseGet(() -> gitAccess.fetchRemoteHeadSha(repository, repository.defaultBranch()));
        //   List<FilePath> changedFiles = repository.lastCommitSha()
        //       .map(known -> gitAccess.changedFilesBetween(repository, known, targetSha))
        //       .orElseGet(() -> gitAccess.listAllFiles(repository, targetSha));  // full scan

        // TODO (2): For each changed file, read content and parse
        //   for (FilePath path : changedFiles) {
        //       String content = gitAccess.readFileContent(repository, path, targetSha);
        //       List<Symbol> symbols = symbolExtractor.extract(repository.identifier(), path, content);
        //       // upsert SourceFile, delete old symbols, insert new symbols
        //   }

        // TODO (3): Update repository's last known commit and scanned timestamp
        //   repository.setLastCommitSha(targetSha);
        //   repository.setLastScannedAt(Instant.now());
        //   repositoryStore.update(repository);

        logger.info("Scan of repository '{}' for job {} completed (stub — no real scanning done)",
                repository.name().value(), job.identifier().value());

        return 0; // stub: no real files scanned
    }
}
