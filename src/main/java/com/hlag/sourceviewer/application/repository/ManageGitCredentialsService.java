package com.hlag.sourceviewer.application.repository;

import com.hlag.sourceviewer.domain.model.identifier.CredentialScopeIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.CredentialScopeType;
import com.hlag.sourceviewer.domain.model.identifier.GitProviderGroupIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.RepositoryIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.SecretValue;
import com.hlag.sourceviewer.domain.model.repository.GitCredential;
import com.hlag.sourceviewer.domain.port.incoming.ManageGitCredentialsUseCase;
import com.hlag.sourceviewer.domain.port.outgoing.GitCredentialStore;
import com.hlag.sourceviewer.domain.port.outgoing.SecretEncryptor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;

/**
 * Implementation of {@link ManageGitCredentialsUseCase} that persists credentials via
 * {@link GitCredentialStore} and protects secrets via {@link SecretEncryptor}.
 */
@ApplicationScoped
public class ManageGitCredentialsService implements ManageGitCredentialsUseCase {

    private static final Logger logger = LoggerFactory.getLogger(ManageGitCredentialsService.class);

    private final GitCredentialStore gitCredentialStore;
    private final SecretEncryptor secretEncryptor;

    @Inject
    public ManageGitCredentialsService(GitCredentialStore gitCredentialStore, SecretEncryptor secretEncryptor) {
        this.gitCredentialStore = gitCredentialStore;
        this.secretEncryptor = secretEncryptor;
    }

    /** @inheritDoc */
    @Override
    public Optional<GitCredential> findCredentialForRepository(RepositoryIdentifier identifier) {
        return gitCredentialStore.findByScope(
                CredentialScopeType.REPOSITORY,
                new CredentialScopeIdentifier(identifier.value()));
    }

    /** @inheritDoc */
    @Override
    public Optional<GitCredential> findCredentialForGroup(GitProviderGroupIdentifier identifier) {
        return gitCredentialStore.findByScope(
                CredentialScopeType.GROUP,
                new CredentialScopeIdentifier(identifier.value()));
    }

    /** @inheritDoc */
    @Override
    public Optional<GitCredential> findCloneCredentialForGroup(GitProviderGroupIdentifier identifier) {
        return gitCredentialStore.findByScope(
                CredentialScopeType.GROUP_CLONE,
                new CredentialScopeIdentifier(identifier.value()));
    }

    /** @inheritDoc */
    @Override
    @Transactional
    public GitCredential setCredentialForRepository(RepositoryIdentifier identifier, SetCredentialCommand command) {
        return setCredential(
                CredentialScopeType.REPOSITORY,
                new CredentialScopeIdentifier(identifier.value()),
                command);
    }

    /** @inheritDoc */
    @Override
    @Transactional
    public GitCredential setCredentialForGroup(GitProviderGroupIdentifier identifier, SetCredentialCommand command) {
        return setCredential(
                CredentialScopeType.GROUP,
                new CredentialScopeIdentifier(identifier.value()),
                command);
    }

    /** @inheritDoc */
    @Override
    @Transactional
    public GitCredential setCloneCredentialForGroup(GitProviderGroupIdentifier identifier, SetCredentialCommand command) {
        return setCredential(
                CredentialScopeType.GROUP_CLONE,
                new CredentialScopeIdentifier(identifier.value()),
                command);
    }

    /** @inheritDoc */
    @Override
    @Transactional
    public void removeCredentialForRepository(RepositoryIdentifier identifier) {
        gitCredentialStore.findByScope(
                        CredentialScopeType.REPOSITORY,
                        new CredentialScopeIdentifier(identifier.value()))
                .ifPresent(credential -> {
                    gitCredentialStore.delete(credential.identifier());
                    logger.info("Removed credential for repository {}", identifier.value());
                });
    }

    /** @inheritDoc */
    @Override
    @Transactional
    public void removeCredentialForGroup(GitProviderGroupIdentifier identifier) {
        gitCredentialStore.findByScope(
                        CredentialScopeType.GROUP,
                        new CredentialScopeIdentifier(identifier.value()))
                .ifPresent(credential -> {
                    gitCredentialStore.delete(credential.identifier());
                    logger.info("Removed API credential for group {}", identifier.value());
                });
    }

    /** @inheritDoc */
    @Override
    @Transactional
    public void removeCloneCredentialForGroup(GitProviderGroupIdentifier identifier) {
        gitCredentialStore.findByScope(
                        CredentialScopeType.GROUP_CLONE,
                        new CredentialScopeIdentifier(identifier.value()))
                .ifPresent(credential -> {
                    gitCredentialStore.delete(credential.identifier());
                    logger.info("Removed clone credential for group {}", identifier.value());
                });
    }

    /** @inheritDoc */
    @Override
    public Optional<SecretValue> resolveRepositorySecret(RepositoryIdentifier identifier) {
        return gitCredentialStore.findByScope(
                        CredentialScopeType.REPOSITORY,
                        new CredentialScopeIdentifier(identifier.value()))
                .map(credential -> secretEncryptor.decrypt(credential.encryptedSecret()));
    }

    /** @inheritDoc */
    @Override
    public Optional<SecretValue> resolveGroupSecret(GitProviderGroupIdentifier identifier) {
        return gitCredentialStore.findByScope(
                        CredentialScopeType.GROUP,
                        new CredentialScopeIdentifier(identifier.value()))
                .map(credential -> secretEncryptor.decrypt(credential.encryptedSecret()));
    }

    /** @inheritDoc */
    @Override
    public Optional<SecretValue> resolveGroupCloneSecret(GitProviderGroupIdentifier identifier) {
        // Prefer the dedicated clone credential; fall back to the API credential.
        var cloneCredential = gitCredentialStore.findByScope(
                CredentialScopeType.GROUP_CLONE,
                new CredentialScopeIdentifier(identifier.value()));
        if (cloneCredential.isPresent()) {
            return cloneCredential.map(c -> secretEncryptor.decrypt(c.encryptedSecret()));
        }
        return resolveGroupSecret(identifier);
    }

    private GitCredential setCredential(
            CredentialScopeType scopeType,
            CredentialScopeIdentifier scopeIdentifier,
            SetCredentialCommand command) {
        var encrypted = secretEncryptor.encrypt(command.secret());
        var now = Instant.now();
        var existing = gitCredentialStore.findByScope(scopeType, scopeIdentifier);
        if (existing.isPresent()) {
            var credential = existing.get();
            credential.setDescription(command.description().orElse(null));
            credential.setEncryptedSecret(encrypted);
            credential.setUpdatedAt(now);
            gitCredentialStore.update(credential);
            logger.info("Updated credential for {} {}", scopeType, scopeIdentifier.value());
            return credential;
        } else {
            var credential = new GitCredential(scopeType, scopeIdentifier, command.description(), encrypted, now);
            var credentialIdentifier = gitCredentialStore.insert(credential);
            logger.info("Created credential for {} {} with id {}",
                    scopeType, scopeIdentifier.value(), credentialIdentifier.value());
            return gitCredentialStore.findByIdentifier(credentialIdentifier)
                    .orElseThrow(() -> new IllegalStateException(
                            "Credential not found after insert: " + credentialIdentifier.value()));
        }
    }
}
