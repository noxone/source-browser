package com.hlag.sourceviewer.domain.model.identifier;

/**
 * Unique identifier for a {@code GitCredential} entity.
 */
public record GitCredentialIdentifier(Long value) implements ValueObject<Long> {
    public GitCredentialIdentifier {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("GitCredentialIdentifier must be a positive number");
        }
    }
}
