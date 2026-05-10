package com.hlag.sourceviewer.adapter.outgoing.persistence;

import com.hlag.sourceviewer.domain.model.identifier.DisplayName;
import com.hlag.sourceviewer.domain.model.identifier.GitProviderGroupIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.RepositoryIdentifier;
import com.hlag.sourceviewer.domain.model.repository.Repository;
import com.hlag.sourceviewer.domain.port.outgoing.RepositoryStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

// Cannot extend PanacheRepositoryBase here because its findAll() returns PanacheQuery<T>,
// which conflicts with RepositoryStore.findAll() returning List<T>.
@ApplicationScoped
public class PanacheRepositoryStore implements RepositoryStore {

    @Inject
    EntityManager em;

    @Override
    public Optional<Repository> findByIdentifier(RepositoryIdentifier identifier) {
        return Optional.ofNullable(em.find(Repository.class, identifier.value()));
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
    public List<Repository> findAllManual() {
        return em.createQuery("from Repository r where r.sourceGroup is null", Repository.class).getResultList();
    }

    @Override
    public List<Repository> findByGroup(GitProviderGroupIdentifier groupIdentifier) {
        return em.createQuery("from Repository r where r.sourceGroup.id = :groupId", Repository.class)
                .setParameter("groupId", groupIdentifier.value())
                .getResultList();
    }

    @Override
    public long countByGroup(GitProviderGroupIdentifier groupIdentifier) {
        return em.createQuery("select count(r) from Repository r where r.sourceGroup.id = :groupId", Long.class)
                .setParameter("groupId", groupIdentifier.value())
                .getSingleResult();
    }

    @Override
    @Transactional
    public void deleteStaleGroupRepositories(GitProviderGroupIdentifier groupIdentifier, Set<String> activeRemoteUrls) {
        var stale = findByGroup(groupIdentifier).stream()
                .filter(r -> r.remoteUrl().map(u -> !activeRemoteUrls.contains(u.value())).orElse(true))
                .toList();
        stale.forEach(r -> {
            var managed = em.find(Repository.class, r.identifier().value());
            if (managed != null) em.remove(managed);
        });
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

    /** @inheritDoc */
    @Override
    @Transactional
    public void delete(RepositoryIdentifier identifier) {
        Repository entity = em.find(Repository.class, identifier.value());
        if (entity != null) {
            em.remove(entity);
        }
    }
}
