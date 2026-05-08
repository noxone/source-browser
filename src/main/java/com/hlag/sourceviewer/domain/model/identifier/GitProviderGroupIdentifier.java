package com.hlag.sourceviewer.domain.model.identifier;

/**
 * Unique identifier of a Git provider group configuration entry.
 */
public record GitProviderGroupIdentifier(Long value) implements ValueObject<Long> {
    public GitProviderGroupIdentifier {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(
                "GitProviderGroupIdentifier must be a positive number, was: " + value);
        }
    }
}
