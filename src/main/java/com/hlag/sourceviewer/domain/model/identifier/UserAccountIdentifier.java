package com.hlag.sourceviewer.domain.model.identifier;

/**
 * Unique identifier of a user account.
 */
public record UserAccountIdentifier(Long value) implements ValueObject<Long> {
    public UserAccountIdentifier {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(
                "UserAccountIdentifier must be a positive number, was: " + value);
        }
    }
}
