package com.hlag.sourceviewer.domain.model.identifier;

/**
 * Plaintext value of a Git credential secret.
 *
 * <p>This wrapper is used only at runtime (for encryption and for passing to the
 * Git adapter). It is never persisted and must never appear in any API response or DTO.</p>
 */
public record SecretValue(String value) implements ValueObject<String> {
    public SecretValue {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("SecretValue must not be blank");
        }
    }
}
