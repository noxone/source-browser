package com.hlag.sourceviewer.domain.port.outgoing;

import com.hlag.sourceviewer.domain.model.identifier.CredentialScopeIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.CredentialScopeType;
import com.hlag.sourceviewer.domain.model.identifier.GitCredentialIdentifier;
import com.hlag.sourceviewer.domain.model.repository.GitCredential;

import java.util.Optional;

/**
 * Port for accessing persisted Git credentials.
 */
public interface GitCredentialStore {

    /**
     * Returns the credential for the given scope, or empty if none is configured.
     *
     * @param scopeType       whether to look up a repository or group credential
     * @param scopeIdentifier numeric identifier of the owning entity
     * @return the matching credential, or empty
     */
    Optional<GitCredential> findByScope(CredentialScopeType scopeType, CredentialScopeIdentifier scopeIdentifier);

    /**
     * Returns the credential with the given identifier, or empty if it does not exist.
     *
     * @param identifier the credential identifier to look up
     * @return the matching credential, or empty
     */
    Optional<GitCredential> findByIdentifier(GitCredentialIdentifier identifier);

    /**
     * Persists a new credential and returns its generated identifier.
     *
     * @param credential the credential to persist
     * @return the generated identifier assigned to the persisted credential
     */
    GitCredentialIdentifier insert(GitCredential credential);

    /**
     * Updates an existing credential in the store.
     *
     * @param credential the credential with updated field values
     */
    void update(GitCredential credential);

    /**
     * Removes the credential with the given identifier.
     * Does nothing if no credential with that identifier exists.
     *
     * @param identifier the identifier of the credential to remove
     */
    void delete(GitCredentialIdentifier identifier);
}
