package com.hlag.sourceviewer.domain.model.token;

import com.hlag.sourceviewer.domain.model.identifier.PersonalAccessTokenIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.PrincipalName;
import com.hlag.sourceviewer.domain.model.identifier.TokenHash;
import com.hlag.sourceviewer.domain.model.identifier.TokenName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Optional;

/**
 * A personal access token that allows API access without an interactive OIDC login.
 * Only the SHA-256 hash of the raw token is persisted; the raw value is returned once at creation.
 */
@Entity
@Table(name = "personal_access_token")
public class PersonalAccessToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner", nullable = false)
    private PrincipalName owner;

    @Column(name = "name", nullable = false)
    private TokenName name;

    @Column(name = "token_hash", nullable = false, unique = true)
    private TokenHash tokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected PersonalAccessToken() {}

    public PersonalAccessToken(
            PrincipalName owner,
            TokenName name,
            TokenHash tokenHash,
            Instant createdAt,
            Optional<Instant> expiresAt) {
        this.owner = owner;
        this.name = name;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt.orElse(null);
    }

    /**
     * Returns whether this token has passed its expiry time.
     *
     * @return {@code true} if the token has an expiry date that is in the past
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public PersonalAccessTokenIdentifier identifier() {
        return id != null ? new PersonalAccessTokenIdentifier(id) : null;
    }

    public PrincipalName owner() { return owner; }
    public TokenName name() { return name; }
    public TokenHash tokenHash() { return tokenHash; }
    public Instant createdAt() { return createdAt; }
    public Optional<Instant> expiresAt() { return Optional.ofNullable(expiresAt); }
}
