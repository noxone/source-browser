package com.hlag.sourceviewer.adapter.outgoing.persistence;

import com.hlag.sourceviewer.domain.model.identifier.GitProviderGroupIdentifier;
import com.hlag.sourceviewer.domain.model.repository.GitProviderGroup;
import com.hlag.sourceviewer.domain.port.outgoing.GitProviderGroupStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * JPA-based implementation of {@link GitProviderGroupStore} using the Quarkus EntityManager.
 */
@ApplicationScoped
public class PanacheGitProviderGroupStore implements GitProviderGroupStore {

    @Inject
    EntityManager em;

    /** @inheritDoc */
    @Override
    public Optional<GitProviderGroup> findByIdentifier(GitProviderGroupIdentifier identifier) {
        return Optional.ofNullable(em.find(GitProviderGroup.class, identifier.value()));
    }

    /** @inheritDoc */
    @Override
    public List<GitProviderGroup> findAll() {
        return em.createQuery("from GitProviderGroup", GitProviderGroup.class).getResultList();
    }

    /** @inheritDoc */
    @Override
    @Transactional
    public GitProviderGroupIdentifier insert(GitProviderGroup group) {
        em.persist(group);
        return group.identifier();
    }

    /** @inheritDoc */
    @Override
    @Transactional
    public void update(GitProviderGroup group) {
        em.merge(group);
    }

    /** @inheritDoc */
    @Override
    @Transactional
    public void delete(GitProviderGroupIdentifier identifier) {
        GitProviderGroup entity = em.find(GitProviderGroup.class, identifier.value());
        if (entity != null) {
            em.remove(entity);
        }
    }
}
