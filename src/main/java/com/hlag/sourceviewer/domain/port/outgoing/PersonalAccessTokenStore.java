package com.hlag.sourceviewer.domain.port.outgoing;

import com.hlag.sourceviewer.domain.model.identifier.PersonalAccessTokenIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.PrincipalName;
import com.hlag.sourceviewer.domain.model.identifier.TokenHash;
import com.hlag.sourceviewer.domain.model.token.PersonalAccessToken;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for personal access tokens.
 */
public interface PersonalAccessTokenStore {

    /**
     * Returns all tokens owned by the given principal, ordered by creation date descending.
     *
     * @param owner the owner's principal name
     * @return list of tokens, never null
     */
    List<PersonalAccessToken> findByOwner(PrincipalName owner);

    /**
     * Returns the token matching the given SHA-256 hash, if it exists.
     *
     * @param tokenHash the SHA-256 hash of the raw token value
     * @return the matching token, or empty
     */
    Optional<PersonalAccessToken> findByTokenHash(TokenHash tokenHash);

    /**
     * Persists a new token and returns its assigned identifier.
     *
     * @param token the token to persist
     * @return the generated identifier
     */
    PersonalAccessTokenIdentifier insert(PersonalAccessToken token);

    /**
     * Removes the token with the given identifier.
     * Does nothing if no such token exists.
     *
     * @param identifier the identifier of the token to remove
     * @param owner      the owner, used to prevent cross-user deletion
     */
    void delete(PersonalAccessTokenIdentifier identifier, PrincipalName owner);
}
