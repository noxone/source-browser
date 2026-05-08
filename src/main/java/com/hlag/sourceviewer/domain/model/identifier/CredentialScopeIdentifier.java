package com.hlag.sourceviewer.domain.model.identifier;

/**
 * Numeric identifier of the entity (repository or group) that a {@code GitCredential} is scoped to.
 *
 * <p>Together with {@link CredentialScopeType} this forms a unique key for each credential.</p>
 */
public record CredentialScopeIdentifier(Long value) implements ValueObject<Long> {
    public CredentialScopeIdentifier {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("CredentialScopeIdentifier must be a positive number");
        }
    }
}
