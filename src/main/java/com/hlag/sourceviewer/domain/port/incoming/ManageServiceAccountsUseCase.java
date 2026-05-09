package com.hlag.sourceviewer.domain.port.incoming;

import com.hlag.sourceviewer.domain.model.identifier.PersonalAccessTokenIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.TokenName;
import com.hlag.sourceviewer.domain.model.identifier.UserAccountIdentifier;
import com.hlag.sourceviewer.domain.model.token.PersonalAccessToken;
import com.hlag.sourceviewer.domain.model.user.UserAccount;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Use case for managing service accounts and their personal access tokens.
 *
 * <p>Service accounts are machine-only principals (bots, CI systems) that cannot log in
 * through the UI. They are created and managed exclusively by administrators.</p>
 */
public interface ManageServiceAccountsUseCase {

    /**
     * The prefix applied to all service account principal names to prevent collision
     * with OIDC identities provisioned through interactive login.
     */
    String SERVICE_ACCOUNT_PRINCIPAL_PREFIX = "svc:";

    /**
     * Creates a new service account with the given name and role.
     * The stored principal name is prefixed with {@code svc:} to prevent collision
     * with OIDC identities.
     *
     * @param command the creation parameters
     * @return the newly created service account
     * @throws IllegalArgumentException if the name is invalid or already taken
     */
    UserAccount createServiceAccount(CreateServiceAccountCommand command);

    /**
     * Returns all service accounts ordered by creation date ascending.
     *
     * @return list of service accounts, never null
     */
    List<UserAccount> listServiceAccounts();

    /**
     * Returns the service account with the given identifier, if it exists.
     *
     * @param identifier the account identifier
     * @return the matching service account, or empty
     */
    Optional<UserAccount> findServiceAccount(UserAccountIdentifier identifier);

    /**
     * Grants or revokes administrator privileges for the service account with the given identifier.
     *
     * @param identifier the identifier of the account to update
     * @param admin      {@code true} to grant admin, {@code false} to revoke
     * @return the updated account
     * @throws java.util.NoSuchElementException if no service account with the given identifier exists
     */
    UserAccount setAdminStatus(UserAccountIdentifier identifier, boolean admin);

    /**
     * Deletes the service account with the given identifier and all of its personal access tokens.
     * Does nothing if no such account exists.
     *
     * @param identifier the identifier of the account to delete
     */
    void deleteServiceAccount(UserAccountIdentifier identifier);

    /**
     * Returns all personal access tokens belonging to the given service account.
     *
     * @param identifier the service account identifier
     * @return list of tokens, never null
     * @throws java.util.NoSuchElementException if no service account with the given identifier exists
     */
    List<PersonalAccessToken> listTokens(UserAccountIdentifier identifier);

    /**
     * Creates a new personal access token for the given service account.
     * The raw token value is returned once and cannot be retrieved again.
     *
     * @param identifier the service account identifier
     * @param command    the token creation parameters
     * @return the creation result containing the entity and the raw token (shown once)
     * @throws java.util.NoSuchElementException if no service account with the given identifier exists
     */
    ManagePersonalAccessTokensUseCase.TokenCreationResult createToken(
            UserAccountIdentifier identifier, CreateTokenCommand command);

    /**
     * Revokes the personal access token with the given identifier.
     * Does nothing if the token does not exist.
     *
     * @param tokenIdentifier the identifier of the token to revoke
     */
    void revokeToken(PersonalAccessTokenIdentifier tokenIdentifier);

    /**
     * Parameters for creating a new service account.
     *
     * @param name  a short identifier for the account (alphanumeric, hyphens, underscores; max 64 chars)
     * @param admin whether the account should have administrator privileges
     */
    record CreateServiceAccountCommand(String name, boolean admin) {}

    /**
     * Parameters for creating a new personal access token for a service account.
     *
     * @param name      a human-readable label for the token
     * @param expiresAt optional expiry instant; empty means the token never expires
     */
    record CreateTokenCommand(TokenName name, Optional<Instant> expiresAt) {}
}
