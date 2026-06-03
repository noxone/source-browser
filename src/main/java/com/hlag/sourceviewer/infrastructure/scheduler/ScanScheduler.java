package com.hlag.sourceviewer.infrastructure.scheduler;

import com.hlag.sourceviewer.domain.model.identifier.CommitSha;
import com.hlag.sourceviewer.domain.model.source.ScanJob;
import com.hlag.sourceviewer.domain.port.incoming.ManageGitProviderGroupsUseCase;
import com.hlag.sourceviewer.domain.port.incoming.ScanRepositoryUseCase;
import com.hlag.sourceviewer.domain.port.incoming.SyncGroupRepositoriesUseCase;
import com.hlag.sourceviewer.domain.port.outgoing.GitAccess;
import com.hlag.sourceviewer.domain.port.outgoing.RepositoryStore;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Cron-based scan trigger as a safety net for missed webhooks.
 *
 * <p>Checks every 15 minutes whether the HEAD commit of a repository has
 * changed compared to the last known commit and queues a new scan job if needed.</p>
 */
@ApplicationScoped
public class ScanScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ScanScheduler.class);

    private final RepositoryStore repositoryStore;
    private final GitAccess gitAccess;
    private final ScanRepositoryUseCase scanRepositoryUseCase;
    private final ManageGitProviderGroupsUseCase manageGitProviderGroupsUseCase;
    private final SyncGroupRepositoriesUseCase syncGroupRepositoriesUseCase;

    @Inject
    public ScanScheduler(
            RepositoryStore repositoryStore,
            GitAccess gitAccess,
            ScanRepositoryUseCase scanRepositoryUseCase,
            ManageGitProviderGroupsUseCase manageGitProviderGroupsUseCase,
            SyncGroupRepositoriesUseCase syncGroupRepositoriesUseCase) {
        this.repositoryStore = repositoryStore;
        this.gitAccess = gitAccess;
        this.scanRepositoryUseCase = scanRepositoryUseCase;
        this.manageGitProviderGroupsUseCase = manageGitProviderGroupsUseCase;
        this.syncGroupRepositoriesUseCase = syncGroupRepositoriesUseCase;
    }

    @Scheduled(cron = "0 0 2 * * ?", concurrentExecution = ConcurrentExecution.SKIP)
    public void syncAllGroups() {
        logger.info("Daily group sync started");
        manageGitProviderGroupsUseCase.listGitProviderGroups().forEach(group -> {
            try {
                syncGroupRepositoriesUseCase.syncGroup(group.identifier());
            } catch (Exception e) {
                logger.error("Daily group sync failed for group '{}': {}",
                        group.groupPath().value(), e.getMessage(), e);
            }
        });
    }

    @Scheduled(cron = "0 0 * * * ?", concurrentExecution = ConcurrentExecution.SKIP)
    public void detectChangesInAllRepositories() {
        logger.debug("Cron scan started");

        repositoryStore.findAll().forEach(repository -> {
            try {
                if (!gitAccess.localRepositoryExists(repository)) {
                    logger.info("Repository '{}' not yet cloned locally, scheduling initial scan",
                            repository.name().value());
                    scanRepositoryUseCase.enqueueScan(
                            new ScanRepositoryUseCase.ScanCommand(
                                    repository.identifier(),
                                    Optional.empty(),
                                    ScanJob.TriggerType.CRON,
                                    false
                            ));
                    return;
                }

                CommitSha remoteHeadSha = gitAccess.fetchRemoteHeadSha(
                        repository, repository.defaultBranch());

                boolean hasNewCommit = repository.lastCommitSha()
                        .map(knownSha -> !knownSha.value().equals(remoteHeadSha.value()))
                        .orElse(true);

                if (hasNewCommit) {
                    logger.info("New commit detected in repository {}: {}",
                            repository.name().value(),
                            remoteHeadSha.shortForm());

                    scanRepositoryUseCase.enqueueScan(
                            new ScanRepositoryUseCase.ScanCommand(
                                    repository.identifier(),
                                    Optional.of(remoteHeadSha),
                                    ScanJob.TriggerType.CRON,
                                    false
                            ));
                }
            } catch (Exception exception) {
                logger.error("Error during cron scan for repository {}: {}",
                        repository.name().value(), exception.getMessage(), exception);
            }
        });
    }
}
