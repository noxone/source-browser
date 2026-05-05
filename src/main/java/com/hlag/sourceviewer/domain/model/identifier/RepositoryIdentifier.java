package com.hlag.sourceviewer.domain.model.identifier;

/**
 * Unique identifier of a repository entry.
 */
public record RepositoryIdentifier(Long value) implements ValueObject<Long> {
    public RepositoryIdentifier {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(
                "RepositoryIdentifier must be a positive number, was: " + value);
        }
    }
}
