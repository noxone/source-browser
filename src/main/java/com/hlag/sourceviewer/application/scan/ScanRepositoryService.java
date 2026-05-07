package com.hlag.sourceviewer.application.scan;

import com.hlag.sourceviewer.domain.model.identifier.TokenCount;
import com.hlag.sourceviewer.domain.model.source.ScanJob;
import com.hlag.sourceviewer.domain.port.incoming.ScanRepositoryUseCase;
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
 * Implementation of {@link ScanRepositoryUseCase}.
 * Queues new scan jobs.
 */
@ApplicationScoped
public class ScanRepositoryService implements ScanRepositoryUseCase {

    private static final Logger logger = LoggerFactory.getLogger(ScanRepositoryService.class);

    private final RepositoryStore repositoryStore;
    private final ScanJobRepository scanJobRepository;

    @Inject
    public ScanRepositoryService(
            RepositoryStore repositoryStore,
            ScanJobRepository scanJobRepository) {
        this.repositoryStore = repositoryStore;
        this.scanJobRepository = scanJobRepository;
    }

    @Override
    @Transactional
    public ScanJob enqueueScan(ScanCommand command) {
        var repository = repositoryStore.findByIdentifier(command.repositoryIdentifier())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Repository not found: " + command.repositoryIdentifier().value()));

        var scanJob = new ScanJob(
                command.repositoryIdentifier(),
                command.triggerType(),
                command.commitSha(),
                ScanJob.ScanJobStatus.QUEUED,
                Instant.now(),
                Optional.empty(),
                Optional.empty(),
                new TokenCount(0),
                Optional.empty()
        );

        scanJobRepository.insert(scanJob);

        logger.info("Scan job {} queued for repository {} (trigger: {})",
                scanJob.identifier().value(),
                command.repositoryIdentifier().value(),
                command.triggerType());

        return scanJob;
    }
}
