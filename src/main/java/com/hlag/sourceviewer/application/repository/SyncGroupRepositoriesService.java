package com.hlag.sourceviewer.application.repository;

import com.hlag.sourceviewer.domain.model.identifier.BranchName;
import com.hlag.sourceviewer.domain.model.identifier.DisplayName;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import com.hlag.sourceviewer.domain.model.identifier.GitProviderGroupIdentifier;
import com.hlag.sourceviewer.domain.model.repository.DiscoveredRepository;
import com.hlag.sourceviewer.domain.model.repository.GitProviderGroup;
import com.hlag.sourceviewer.domain.model.repository.Repository;
import com.hlag.sourceviewer.domain.port.incoming.ManageGitCredentialsUseCase;
import com.hlag.sourceviewer.domain.port.incoming.ManageGitProviderGroupsUseCase;
import com.hlag.sourceviewer.domain.port.incoming.SyncGroupRepositoriesUseCase;
import com.hlag.sourceviewer.domain.port.outgoing.GitProviderGroupClient;
import com.hlag.sourceviewer.domain.port.outgoing.RepositoryStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Synchronises the repository list of a Git provider group by querying the provider API
 * and upserting results into the local store.
 */
@ApplicationScoped
public class SyncGroupRepositoriesService implements SyncGroupRepositoriesUseCase {

    private static final Logger logger = LoggerFactory.getLogger(SyncGroupRepositoriesService.class);

    private final ManageGitProviderGroupsUseCase manageGitProviderGroupsUseCase;
    private final ManageGitCredentialsUseCase manageGitCredentialsUseCase;
    private final GitProviderGroupClient gitProviderGroupClient;
    private final RepositoryStore repositoryStore;

    @Inject
    public SyncGroupRepositoriesService(
            ManageGitProviderGroupsUseCase manageGitProviderGroupsUseCase,
            ManageGitCredentialsUseCase manageGitCredentialsUseCase,
            GitProviderGroupClient gitProviderGroupClient,
            RepositoryStore repositoryStore) {
        this.manageGitProviderGroupsUseCase = manageGitProviderGroupsUseCase;
        this.manageGitCredentialsUseCase = manageGitCredentialsUseCase;
        this.gitProviderGroupClient = gitProviderGroupClient;
        this.repositoryStore = repositoryStore;
    }

    /** @inheritDoc */
    @Override
    @Transactional
    public void syncGroup(GitProviderGroupIdentifier identifier) {
        GitProviderGroup group = manageGitProviderGroupsUseCase.findGitProviderGroup(identifier)
                .orElseThrow(() -> new NoSuchElementException("Git provider group not found: " + identifier.value()));

        String apiSecret = manageGitCredentialsUseCase.resolveGroupSecret(identifier)
                .map(s -> s.value())
                .orElseThrow(() -> new IllegalStateException(
                        "No API credential configured for group: " + identifier.value()));

        logger.info("Syncing repositories for group '{}' (id={})", group.groupPath().value(), identifier.value());
        List<DiscoveredRepository> discovered = gitProviderGroupClient.discoverRepositories(group, apiSecret);

        List<Repository> existing = repositoryStore.findByGroup(identifier);

        int inserted = 0;
        int updated = 0;
        for (DiscoveredRepository repo : discovered) {
            Optional<Repository> match = existing.stream()
                    .filter(r -> r.remoteUrl().map(u -> u.value().equals(repo.remoteUrl())).orElse(false))
                    .findFirst();
            if (match.isPresent()) {
                Repository r = match.get();
                boolean changed = false;
                if (!r.name().value().equals(repo.name())) {
                    r.setName(new DisplayName(repo.name()));
                    changed = true;
                }
                if (!r.defaultBranch().value().equals(repo.defaultBranch())) {
                    r.setDefaultBranch(new BranchName(repo.defaultBranch()));
                    changed = true;
                }
                if (changed) {
                    repositoryStore.update(r);
                    updated++;
                }
            } else {
                var newRepo = new Repository(
                        new DisplayName(repo.name()),
                        Optional.of(new FilePath(repo.remoteUrl())),
                        new BranchName(repo.defaultBranch()),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(group)
                );
                repositoryStore.insert(newRepo);
                inserted++;
            }
        }

        Set<String> activeUrls = discovered.stream()
                .map(DiscoveredRepository::remoteUrl)
                .collect(Collectors.toSet());
        repositoryStore.deleteStaleGroupRepositories(identifier, activeUrls);
        int deleted = (int) existing.stream()
                .filter(r -> r.remoteUrl().map(u -> !activeUrls.contains(u.value())).orElse(true))
                .count();

        logger.info("Group '{}' sync complete: {} inserted, {} updated, {} deleted",
                group.groupPath().value(), inserted, updated, deleted);
    }
}
