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
    @SuppressWarnings("unchecked")
    public List<UserAccount> findPage(String principalNameFilter, int offset, int limit) {
        String pattern = "%" + principalNameFilter.toLowerCase() + "%";
        return em.createNativeQuery(
                "SELECT * FROM user_account WHERE LOWER(principal_name) LIKE :pattern ORDER BY created_at ASC",
                UserAccount.class)
            .setParameter("pattern", pattern)
            .setFirstResult(offset)
            .setMaxResults(limit)
            .getResultList();
    }

    /** @inheritDoc */
    @Override
    public long countMatching(String principalNameFilter) {
        String pattern = "%" + principalNameFilter.toLowerCase() + "%";
        return ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM user_account WHERE LOWER(principal_name) LIKE :pattern")
            .setParameter("pattern", pattern)
            .getSingleResult()).longValue();
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

    /** @inheritDoc */
    @Override
    @Transactional
    public void deleteById(UserAccountIdentifier identifier) {
        em.createQuery("delete from UserAccount where id = :id")
            .setParameter("id", identifier.value())
            .executeUpdate();
    }
}
