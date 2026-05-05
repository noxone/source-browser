package com.hlag.sourceviewer.adapter.outgoing.persistence;

import com.hlag.sourceviewer.domain.model.identifier.DisplayName;
import com.hlag.sourceviewer.domain.model.identifier.RepositoryIdentifier;
import com.hlag.sourceviewer.domain.model.repository.Repository;
import com.hlag.sourceviewer.domain.port.outgoing.RepositoryStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

// Cannot extend PanacheRepositoryBase here because its findAll() returns PanacheQuery<T>,
// which conflicts with RepositoryStore.findAll() returning List<T>.
@ApplicationScoped
public class PanacheRepositoryStore implements RepositoryStore {

    @Inject
    EntityManager em;

    @Override
    public Optional<Repository> findByIdentifier(RepositoryIdentifier identifier) {
        return Optional.ofNullable(em.find(Repository.class, identifier));
    }

    @Override
    public Optional<Repository> findByName(DisplayName name) {
        return em.createQuery("from Repository where name = :name", Repository.class)
                .setParameter("name", name)
                .getResultStream()
                .findFirst();
    }

    @Override
    public List<Repository> findAll() {
        return em.createQuery("from Repository", Repository.class).getResultList();
    }

    @Override
    @Transactional
    public RepositoryIdentifier insert(Repository repository) {
        em.persist(repository);
        return repository.identifier();
    }

    @Override
    @Transactional
    public void update(Repository repository) {
        em.merge(repository);
    }
}
