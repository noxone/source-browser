package com.hlag.sourceviewer.application.repository;

import com.hlag.sourceviewer.domain.model.identifier.GitProviderGroupIdentifier;
import com.hlag.sourceviewer.domain.model.repository.GitProviderGroup;
import com.hlag.sourceviewer.domain.model.repository.Repository;
import com.hlag.sourceviewer.domain.port.incoming.ManageGitProviderGroupsUseCase;
import com.hlag.sourceviewer.domain.port.outgoing.GitProviderGroupStore;
import com.hlag.sourceviewer.domain.port.outgoing.RepositoryStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Implementation of {@link ManageGitProviderGroupsUseCase} that persists group configurations
 * via the {@link GitProviderGroupStore}.
 */
@ApplicationScoped
public class ManageGitProviderGroupsService implements ManageGitProviderGroupsUseCase {

    private static final Logger logger = LoggerFactory.getLogger(ManageGitProviderGroupsService.class);

    private final GitProviderGroupStore gitProviderGroupStore;
    private final RepositoryStore repositoryStore;

    @Inject
    public ManageGitProviderGroupsService(
            GitProviderGroupStore gitProviderGroupStore,
            RepositoryStore repositoryStore) {
        this.gitProviderGroupStore = gitProviderGroupStore;
        this.repositoryStore = repositoryStore;
    }

    /** @inheritDoc */
    @Override
    public List<GitProviderGroup> listGitProviderGroups() {
        return gitProviderGroupStore.findAll();
    }

    /** @inheritDoc */
    @Override
    public Optional<GitProviderGroup> findGitProviderGroup(GitProviderGroupIdentifier identifier) {
        return gitProviderGroupStore.findByIdentifier(identifier);
    }

    /** @inheritDoc */
    @Override
    @Transactional
    public GitProviderGroup createGitProviderGroup(CreateGitProviderGroupCommand command) {
        var group = new GitProviderGroup(
                command.name(),
                command.providerType(),
                command.groupPath(),
                command.baseUrl(),
                command.archivedOmitted(),
                command.forkedOmitted()
        );
        var identifier = gitProviderGroupStore.insert(group);
        logger.info("Created Git provider group '{}' with id {}", command.name().value(), identifier.value());
        return gitProviderGroupStore.findByIdentifier(identifier)
                .orElseThrow(() -> new IllegalStateException(
                        "Git provider group not found after insert: " + identifier.value()));
    }

    /** @inheritDoc */
    @Override
    @Transactional
    public GitProviderGroup updateGitProviderGroup(UpdateGitProviderGroupCommand command) {
        var group = gitProviderGroupStore.findByIdentifier(command.identifier())
                .orElseThrow(() -> new NoSuchElementException(
                        "Git provider group not found: " + command.identifier().value()));
        group.setName(command.name());
        group.setProviderType(command.providerType());
        group.setGroupPath(command.groupPath());
        group.setBaseUrl(command.baseUrl().orElse(null));
        group.setArchivedOmitted(command.archivedOmitted());
        group.setForkedOmitted(command.forkedOmitted());
        gitProviderGroupStore.update(group);
        logger.info("Updated Git provider group {}", command.identifier().value());
        return group;
    }

    /** @inheritDoc */
    @Override
    @Transactional
    public void deleteGitProviderGroup(GitProviderGroupIdentifier identifier) {
        gitProviderGroupStore.delete(identifier);
        logger.info("Deleted Git provider group {}", identifier.value());
    }

    /** @inheritDoc */
    @Override
    public List<Repository> listGroupRepositories(GitProviderGroupIdentifier identifier) {
        return repositoryStore.findByGroup(identifier);
    }

    /** @inheritDoc */
    @Override
    public long countGroupRepositories(GitProviderGroupIdentifier identifier) {
        return repositoryStore.countByGroup(identifier);
    }
}
