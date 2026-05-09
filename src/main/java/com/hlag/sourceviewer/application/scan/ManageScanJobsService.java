package com.hlag.sourceviewer.application.scan;

import com.hlag.sourceviewer.domain.model.identifier.ScanJobIdentifier;
import com.hlag.sourceviewer.domain.model.source.ScanJob;
import com.hlag.sourceviewer.domain.port.incoming.ManageScanJobsUseCase;
import com.hlag.sourceviewer.domain.port.outgoing.ScanJobRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Implementation of {@link ManageScanJobsUseCase}.
 *
 * <p>Provides admin-level listing and deletion of scan jobs. Only {@code QUEUED}
 * jobs may be deleted — jobs that are already running or have finished cannot
 * be removed from the outside.</p>
 */
@ApplicationScoped
public class ManageScanJobsService implements ManageScanJobsUseCase {

    private static final Logger logger = LoggerFactory.getLogger(ManageScanJobsService.class);

    private final ScanJobRepository scanJobRepository;

    @Inject
    public ManageScanJobsService(ScanJobRepository scanJobRepository) {
        this.scanJobRepository = scanJobRepository;
    }

    @Override
    public List<ScanJob> listScanJobs(Optional<ScanJob.ScanJobStatus> status) {
        return status.map(scanJobRepository::findByStatus)
                .orElseGet(scanJobRepository::findAllScanJobs);
    }

    @Override
    @Transactional
    public void deleteScanJob(ScanJobIdentifier identifier) {
        var job = scanJobRepository.findByIdentifier(identifier)
                .orElseThrow(() -> new NoSuchElementException(
                        "Scan job not found: " + identifier.value()));

        if (job.status() != ScanJob.ScanJobStatus.QUEUED) {
            throw new IllegalStateException(
                    "Scan job " + identifier.value() + " cannot be deleted — status is " + job.status());
        }

        scanJobRepository.deleteById(identifier);
        logger.info("Scan job {} deleted by administrator", identifier.value());
    }

    @Override
    @Transactional
    public void deleteAllQueuedScanJobs() {
        scanJobRepository.deleteAllQueued();
        logger.info("All queued scan jobs deleted by administrator");
    }
}
