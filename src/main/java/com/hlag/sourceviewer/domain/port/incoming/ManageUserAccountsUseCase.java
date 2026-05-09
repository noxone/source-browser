package com.hlag.sourceviewer.domain.port.incoming;

import com.hlag.sourceviewer.domain.model.Page;
import com.hlag.sourceviewer.domain.model.identifier.PrincipalName;
import com.hlag.sourceviewer.domain.model.identifier.UserAccountIdentifier;
import com.hlag.sourceviewer.domain.model.user.UserAccount;

import java.util.Optional;

/**
 * Use case for provisioning and managing user accounts.
 */
public interface ManageUserAccountsUseCase {

    /**
     * Returns the account for the given principal, creating one if it does not exist yet.
     * The very first account ever provisioned is automatically granted administrator privileges.
     *
     * @param principalName the authenticated principal's name
     * @return the existing or newly created account
     */
    UserAccount provisionUser(PrincipalName principalName);

    /**
     * Returns the account for the given principal name, if it exists.
     *
     * @param principalName the principal name to look up
     * @return the account, or empty if not yet provisioned
     */
    Optional<UserAccount> findUser(PrincipalName principalName);

    /**
     * Returns a page of provisioned user accounts, optionally filtered by principal name.
     *
     * @param principalNameFilter a substring to match against principal names (case-insensitive),
     *                            or blank to return all accounts
     * @param page                the one-based page number to return
     * @param pageSize            the maximum number of accounts per page
     * @return the requested page of accounts, never null
     */
    Page<UserAccount> listUsers(String principalNameFilter, int page, int pageSize);

    /**
     * Grants or revokes administrator privileges for the account with the given identifier.
     *
     * @param identifier the identifier of the account to update
     * @param admin      {@code true} to grant admin, {@code false} to revoke
     * @return the updated account
     * @throws java.util.NoSuchElementException if no account with the given identifier exists
     */
    UserAccount setAdminStatus(UserAccountIdentifier identifier, boolean admin);
}
