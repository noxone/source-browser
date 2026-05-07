package com.hlag.sourceviewer.application.repository;

import com.hlag.sourceviewer.domain.model.identifier.RepositoryIdentifier;
import com.hlag.sourceviewer.domain.model.repository.Repository;
import com.hlag.sourceviewer.domain.port.incoming.ManageRepositoriesUseCase;
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
 * Implementation of {@link ManageRepositoriesUseCase} that persists repositories via the {@link RepositoryStore}.
 */
@ApplicationScoped
public class ManageRepositoriesService implements ManageRepositoriesUseCase {

    private static final Logger logger = LoggerFactory.getLogger(ManageRepositoriesService.class);

    private final RepositoryStore repositoryStore;

    @Inject
    public ManageRepositoriesService(RepositoryStore repositoryStore) {
        this.repositoryStore = repositoryStore;
    }

    /** @inheritDoc */
    @Override
    public List<Repository> listRepositories() {
        return repositoryStore.findAll();
    }

    /** @inheritDoc */
    @Override
    public Optional<Repository> findRepository(RepositoryIdentifier identifier) {
        return repositoryStore.findByIdentifier(identifier);
    }

    /** @inheritDoc */
    @Override
    @Transactional
    public Repository createRepository(CreateRepositoryCommand command) {
        var repository = new Repository(
                command.name(),
                command.remoteUrl(),
                command.defaultBranch(),
                Optional.empty(),
                Optional.empty()
        );
        var identifier = repositoryStore.insert(repository);
        logger.info("Created repository {} with id {}", command.name().value(), identifier.value());
        return repositoryStore.findByIdentifier(identifier)
                .orElseThrow(() -> new IllegalStateException("Repository not found after insert: " + identifier.value()));
    }

    /** @inheritDoc */
    @Override
    @Transactional
    public Repository updateRepository(UpdateRepositoryCommand command) {
        var repository = repositoryStore.findByIdentifier(command.identifier())
                .orElseThrow(() -> new NoSuchElementException(
                        "Repository not found: " + command.identifier().value()));
        repository.setName(command.name());
        repository.setRemoteUrl(command.remoteUrl().orElse(null));
        repository.setDefaultBranch(command.defaultBranch());
        repositoryStore.update(repository);
        logger.info("Updated repository {}", command.identifier().value());
        return repository;
    }

    /** @inheritDoc */
    @Override
    @Transactional
    public void deleteRepository(RepositoryIdentifier identifier) {
        repositoryStore.delete(identifier);
        logger.info("Deleted repository {}", identifier.value());
    }
}
