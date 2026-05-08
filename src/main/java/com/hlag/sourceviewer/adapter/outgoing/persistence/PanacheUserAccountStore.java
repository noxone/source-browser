package com.hlag.sourceviewer.adapter.outgoing.persistence;

import com.hlag.sourceviewer.domain.model.identifier.PrincipalName;
import com.hlag.sourceviewer.domain.model.identifier.UserAccountIdentifier;
import com.hlag.sourceviewer.domain.model.user.UserAccount;
import com.hlag.sourceviewer.domain.port.outgoing.UserAccountStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * JPA/Panache persistence adapter for user accounts.
 */
@ApplicationScoped
public class PanacheUserAccountStore implements UserAccountStore {

    @Inject
    EntityManager em;

    /** @inheritDoc */
    @Override
    public Optional<UserAccount> findByPrincipalName(PrincipalName principalName) {
        return em.createQuery(
                "from UserAccount where principalName = :p",
                UserAccount.class)
            .setParameter("p", principalName)
            .getResultStream()
            .findFirst();
    }

    /** @inheritDoc */
    @Override
    public Optional<UserAccount> findById(UserAccountIdentifier identifier) {
        return Optional.ofNullable(em.find(UserAccount.class, identifier.value()));
    }

    /** @inheritDoc */
    @Override
    public List<UserAccount> findAll() {
        return em.createQuery(
                "from UserAccount order by createdAt asc",
                UserAccount.class)
            .getResultList();
    }

    /** @inheritDoc */
    @Override
    public long countAll() {
        return em.createQuery("select count(u) from UserAccount u", Long.class)
            .getSingleResult();
    }

    /** @inheritDoc */
    @Override
    @Transactional
    public UserAccountIdentifier insert(UserAccount userAccount) {
        em.persist(userAccount);
        return userAccount.identifier();
    }
}
