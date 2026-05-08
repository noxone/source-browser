package com.hlag.sourceviewer.domain.model.repository;

import com.hlag.sourceviewer.domain.model.identifier.CredentialDescription;
import com.hlag.sourceviewer.domain.model.identifier.CredentialScopeIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.CredentialScopeType;
import com.hlag.sourceviewer.domain.model.identifier.EncryptedSecret;
import com.hlag.sourceviewer.domain.model.identifier.GitCredentialIdentifier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Optional;

/**
 * An encrypted Git credential (e.g. a personal access token or password) scoped to a single
 * repository or Git provider group.
 *
 * <p>The raw secret is never stored in plaintext. Only the AES-256-GCM ciphertext is persisted;
 * the plaintext is available exclusively through the {@code SecretEncryptor} port at runtime.</p>
 */
@Entity
@Table(
        name = "git_credential",
        uniqueConstraints = @UniqueConstraint(
                name = "uidx_git_credential_scope",
                columnNames = {"scope_type", "scope_id"}
        )
)
public class GitCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scope_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private CredentialScopeType scopeType;

    @Column(name = "scope_id", nullable = false)
    private CredentialScopeIdentifier scopeIdentifier;

    @Column(name = "description")
    private CredentialDescription description;

    @Column(name = "encrypted_secret", nullable = false)
    private EncryptedSecret encryptedSecret;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GitCredential() {}

    /**
     * Creates a new Git credential for the given scope.
     *
     * @param scopeType       whether this credential belongs to a repository or a provider group
     * @param scopeIdentifier numeric identifier of the owning entity
     * @param description     optional human-readable label shown in the UI
     * @param encryptedSecret AES-256-GCM ciphertext of the raw secret
     * @param updatedAt       timestamp of the last secret update
     */
    public GitCredential(
            CredentialScopeType scopeType,
            CredentialScopeIdentifier scopeIdentifier,
            Optional<CredentialDescription> description,
            EncryptedSecret encryptedSecret,
            Instant updatedAt) {
        this.scopeType = scopeType;
        this.scopeIdentifier = scopeIdentifier;
        this.description = description.orElse(null);
        this.encryptedSecret = encryptedSecret;
        this.updatedAt = updatedAt;
    }

    /** Returns the unique identifier of this credential, or {@code null} before first persist. */
    public GitCredentialIdentifier identifier() {
        return id != null ? new GitCredentialIdentifier(id) : null;
    }

    /** Returns the scope type (repository or group) this credential belongs to. */
    public CredentialScopeType scopeType() { return scopeType; }

    /** Returns the identifier of the owning entity. */
    public CredentialScopeIdentifier scopeIdentifier() { return scopeIdentifier; }

    /** Returns the optional human-readable description of this credential. */
    public Optional<CredentialDescription> description() { return Optional.ofNullable(description); }

    /** Returns the AES-256-GCM encrypted ciphertext. Never exposed in API responses. */
    public EncryptedSecret encryptedSecret() { return encryptedSecret; }

    /** Returns the timestamp of the last secret update. */
    public Instant updatedAt() { return updatedAt; }

    /** Sets the optional description. Pass {@code null} to clear it. */
    public void setDescription(CredentialDescription description) { this.description = description; }

    /** Replaces the encrypted secret with a new ciphertext. */
    public void setEncryptedSecret(EncryptedSecret encryptedSecret) { this.encryptedSecret = encryptedSecret; }

    /** Updates the last-modified timestamp. */
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
