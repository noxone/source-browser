package com.hlag.sourceviewer.domain.port.incoming;

import com.hlag.sourceviewer.domain.model.identifier.CredentialDescription;
import com.hlag.sourceviewer.domain.model.identifier.GitProviderGroupIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.RepositoryIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.SecretValue;
import com.hlag.sourceviewer.domain.model.repository.GitCredential;

import java.util.Optional;

/**
 * Use case for managing Git credentials scoped to repositories and Git provider groups.
 *
 * <p>No operation on this interface ever returns the plaintext secret to callers.
 * The {@code resolveXxxSecret} methods are intended exclusively for internal use by the
 * Git adapter when authenticating to remote repositories.</p>
 */
public interface ManageGitCredentialsUseCase {

    /**
     * Returns the credential metadata for the given repository, or empty if none is set.
     * The returned entity never exposes the plaintext secret.
     *
     * @param identifier the repository to look up
     * @return the credential metadata, or empty
     */
    Optional<GitCredential> findCredentialForRepository(RepositoryIdentifier identifier);

    /**
     * Returns the credential metadata for the given Git provider group, or empty if none is set.
     * The returned entity never exposes the plaintext secret.
     *
     * @param identifier the group to look up
     * @return the credential metadata, or empty
     */
    Optional<GitCredential> findCredentialForGroup(GitProviderGroupIdentifier identifier);

    /**
     * Creates or replaces the credential for the given repository.
     * The plaintext secret in the command is encrypted before persisting.
     *
     * @param identifier the repository to configure
     * @param command    the new credential data
     * @return the persisted credential metadata (without the plaintext secret)
     */
    GitCredential setCredentialForRepository(RepositoryIdentifier identifier, SetCredentialCommand command);

    /**
     * Creates or replaces the credential for the given Git provider group.
     * The plaintext secret in the command is encrypted before persisting.
     *
     * @param identifier the group to configure
     * @param command    the new credential data
     * @return the persisted credential metadata (without the plaintext secret)
     */
    GitCredential setCredentialForGroup(GitProviderGroupIdentifier identifier, SetCredentialCommand command);

    /**
     * Removes the credential for the given repository.
     * Does nothing if no credential is configured.
     *
     * @param identifier the repository whose credential shall be removed
     */
    void removeCredentialForRepository(RepositoryIdentifier identifier);

    /**
     * Removes the credential for the given Git provider group.
     * Does nothing if no credential is configured.
     *
     * @param identifier the group whose credential shall be removed
     */
    void removeCredentialForGroup(GitProviderGroupIdentifier identifier);

    /**
     * Returns the decrypted secret for the given repository, or empty if none is configured.
     * This method is intended exclusively for the Git adapter and must never be called
     * from REST resources or any code path that could expose the value externally.
     *
     * @param identifier the repository whose secret is needed
     * @return the plaintext secret, or empty
     */
    Optional<SecretValue> resolveRepositorySecret(RepositoryIdentifier identifier);

    /**
     * Returns the decrypted secret for the given Git provider group, or empty if none is configured.
     * This method is intended exclusively for the Git adapter and must never be called
     * from REST resources or any code path that could expose the value externally.
     *
     * @param identifier the group whose secret is needed
     * @return the plaintext secret, or empty
     */
    Optional<SecretValue> resolveGroupSecret(GitProviderGroupIdentifier identifier);

    /**
     * Parameters for creating or replacing a Git credential.
     *
     * @param description optional human-readable label shown in the UI; may be empty
     * @param secret      the plaintext secret (token, password, etc.) to protect
     */
    record SetCredentialCommand(
            Optional<CredentialDescription> description,
            SecretValue secret
    ) {}
}
