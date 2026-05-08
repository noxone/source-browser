package com.hlag.sourceviewer.adapter.outgoing.persistence;

import com.hlag.sourceviewer.domain.model.identifier.CredentialScopeIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.CredentialScopeType;
import com.hlag.sourceviewer.domain.model.identifier.GitCredentialIdentifier;
import com.hlag.sourceviewer.domain.model.repository.GitCredential;
import com.hlag.sourceviewer.domain.port.outgoing.GitCredentialStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.Optional;

/**
 * JPA-based implementation of {@link GitCredentialStore} using the Quarkus EntityManager.
 */
@ApplicationScoped
public class PanacheGitCredentialStore implements GitCredentialStore {

    @Inject
    EntityManager em;

    /** @inheritDoc */
    @Override
    public Optional<GitCredential> findByScope(
            CredentialScopeType scopeType,
            CredentialScopeIdentifier scopeIdentifier) {
        var results = em.createQuery(
                        "from GitCredential where scopeType = :scopeType and scopeIdentifier = :scopeIdentifier",
                        GitCredential.class)
                .setParameter("scopeType", scopeType)
                .setParameter("scopeIdentifier", scopeIdentifier)
                .getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    /** @inheritDoc */
    @Override
    public Optional<GitCredential> findByIdentifier(GitCredentialIdentifier identifier) {
        return Optional.ofNullable(em.find(GitCredential.class, identifier.value()));
    }

    /** @inheritDoc */
    @Override
    @Transactional
    public GitCredentialIdentifier insert(GitCredential credential) {
        em.persist(credential);
        return credential.identifier();
    }

    /** @inheritDoc */
    @Override
    @Transactional
    public void update(GitCredential credential) {
        em.merge(credential);
    }

    /** @inheritDoc */
    @Override
    @Transactional
    public void delete(GitCredentialIdentifier identifier) {
        GitCredential entity = em.find(GitCredential.class, identifier.value());
        if (entity != null) {
            em.remove(entity);
        }
    }
}
