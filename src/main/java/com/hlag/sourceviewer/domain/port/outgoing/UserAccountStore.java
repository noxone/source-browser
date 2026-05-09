package com.hlag.sourceviewer.domain.port.outgoing;

import com.hlag.sourceviewer.domain.model.identifier.PrincipalName;
import com.hlag.sourceviewer.domain.model.identifier.UserAccountIdentifier;
import com.hlag.sourceviewer.domain.model.user.UserAccount;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for user accounts.
 */
public interface UserAccountStore {

    /**
     * Returns the account with the given principal name, if it exists.
     *
     * @param principalName the principal name to look up
     * @return the matching account, or empty
     */
    Optional<UserAccount> findByPrincipalName(PrincipalName principalName);

    /**
     * Returns the account with the given identifier, if it exists.
     *
     * @param identifier the identifier to look up
     * @return the matching account, or empty
     */
    Optional<UserAccount> findById(UserAccountIdentifier identifier);

    /**
     * Returns all accounts ordered by creation date ascending.
     *
     * @return list of all accounts, never null
     */
    List<UserAccount> findAll();

    /**
     * Returns a page of accounts whose principal name contains the given filter string
     * (case-insensitive). An empty filter matches all accounts.
     *
     * @param principalNameFilter a substring to filter on, or empty to match all accounts
     * @param offset              the zero-based index of the first result
     * @param limit               the maximum number of results to return
     * @return matching accounts ordered by creation date ascending
     */
    List<UserAccount> findPage(String principalNameFilter, int offset, int limit);

    /**
     * Returns the number of accounts whose principal name contains the given filter string
     * (case-insensitive). An empty filter counts all accounts.
     *
     * @param principalNameFilter a substring to filter on, or empty to match all accounts
     * @return the count of matching accounts
     */
    long countMatching(String principalNameFilter);

    /**
     * Returns the total number of accounts stored.
     *
     * @return the account count
     */
    long countAll();

    /**
     * Persists a new account and returns its assigned identifier.
     *
     * @param userAccount the account to persist
     * @return the generated identifier
     */
    UserAccountIdentifier insert(UserAccount userAccount);

    /**
     * Removes the account with the given identifier.
     * Does nothing if no such account exists.
     *
     * @param identifier the identifier of the account to remove
     */
    void deleteById(UserAccountIdentifier identifier);
}
