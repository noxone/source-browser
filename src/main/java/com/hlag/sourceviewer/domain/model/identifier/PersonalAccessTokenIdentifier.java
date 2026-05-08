package com.hlag.sourceviewer.domain.model.identifier;

/**
 * Unique identifier of a personal access token.
 */
public record PersonalAccessTokenIdentifier(Long value) implements ValueObject<Long> {
    public PersonalAccessTokenIdentifier {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(
                "PersonalAccessTokenIdentifier must be a positive number, was: " + value);
        }
    }
}
