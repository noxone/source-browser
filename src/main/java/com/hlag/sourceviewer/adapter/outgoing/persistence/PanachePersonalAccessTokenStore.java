package com.hlag.sourceviewer.adapter.outgoing.persistence;

import com.hlag.sourceviewer.domain.model.identifier.PersonalAccessTokenIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.PrincipalName;
import com.hlag.sourceviewer.domain.model.identifier.TokenHash;
import com.hlag.sourceviewer.domain.model.token.PersonalAccessToken;
import com.hlag.sourceviewer.domain.port.outgoing.PersonalAccessTokenStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * JPA/Panache persistence adapter for personal access tokens.
 */
@ApplicationScoped
public class PanachePersonalAccessTokenStore implements PersonalAccessTokenStore {

    @Inject
    EntityManager em;

    /** @inheritDoc */
    @Override
    public List<PersonalAccessToken> findByOwner(PrincipalName owner) {
        return em.createQuery(
                "from PersonalAccessToken where owner = :owner order by createdAt desc",
                PersonalAccessToken.class)
            .setParameter("owner", owner)
            .getResultList();
    }

    /** @inheritDoc */
    @Override
    public Optional<PersonalAccessToken> findByTokenHash(TokenHash tokenHash) {
        return em.createQuery(
                "from PersonalAccessToken where tokenHash = :hash",
                PersonalAccessToken.class)
            .setParameter("hash", tokenHash)
            .getResultStream()
            .findFirst();
    }

    /** @inheritDoc */
    @Override
    @Transactional
    public PersonalAccessTokenIdentifier insert(PersonalAccessToken token) {
        em.persist(token);
        return token.identifier();
    }

    /** @inheritDoc */
    @Override
    @Transactional
    public void delete(PersonalAccessTokenIdentifier identifier, PrincipalName owner) {
        em.createQuery(
                "delete from PersonalAccessToken where id = :id and owner = :owner")
            .setParameter("id", identifier.value())
            .setParameter("owner", owner)
            .executeUpdate();
    }

    /** @inheritDoc */
    @Override
    @Transactional
    public void deleteById(PersonalAccessTokenIdentifier identifier) {
        em.createQuery("delete from PersonalAccessToken where id = :id")
            .setParameter("id", identifier.value())
            .executeUpdate();
    }

    /** @inheritDoc */
    @Override
    @Transactional
    public void deleteAllByOwner(PrincipalName owner) {
        em.createQuery("delete from PersonalAccessToken where owner = :owner")
            .setParameter("owner", owner)
            .executeUpdate();
    }
}
