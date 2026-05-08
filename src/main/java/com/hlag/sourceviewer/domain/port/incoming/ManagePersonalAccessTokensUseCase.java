package com.hlag.sourceviewer.domain.port.incoming;

import com.hlag.sourceviewer.domain.model.identifier.PersonalAccessTokenIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.PrincipalName;
import com.hlag.sourceviewer.domain.model.identifier.TokenName;
import com.hlag.sourceviewer.domain.model.token.PersonalAccessToken;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Use case for creating and revoking personal access tokens.
 */
public interface ManagePersonalAccessTokensUseCase {

    /**
     * Returns all tokens owned by the given principal.
     *
     * @param owner the owner's principal name
     * @return list of tokens owned by the principal
     */
    List<PersonalAccessToken> listTokens(PrincipalName owner);

    /**
     * Creates a new personal access token for the given principal and returns
     * both the persisted token entity and the one-time raw token value.
     *
     * @param command the creation parameters
     * @return the creation result containing the entity and the raw token (shown once)
     */
    TokenCreationResult createToken(CreateTokenCommand command);

    /**
     * Revokes the token with the given identifier if it is owned by the given principal.
     * Does nothing if the token does not exist or belongs to another user.
     *
     * @param identifier the identifier of the token to revoke
     * @param owner      the requesting principal, must match the token's owner
     */
    void revokeToken(PersonalAccessTokenIdentifier identifier, PrincipalName owner);

    /**
     * Parameters for creating a new personal access token.
     *
     * @param owner     the principal creating the token
     * @param name      a human-readable label for the token
     * @param expiresAt optional expiry instant; empty means the token never expires
     */
    record CreateTokenCommand(
            PrincipalName owner,
            TokenName name,
            Optional<Instant> expiresAt
    ) {}

    /**
     * Result of a token creation: the persisted entity plus the raw token value shown once.
     *
     * @param token    the persisted entity (without the raw value)
     * @param rawToken the one-time raw token string that the user must copy immediately
     */
    record TokenCreationResult(PersonalAccessToken token, String rawToken) {}
}
