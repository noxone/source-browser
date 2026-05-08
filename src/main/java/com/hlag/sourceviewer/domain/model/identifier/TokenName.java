package com.hlag.sourceviewer.domain.model.identifier;

/**
 * Human-readable name for a personal access token (e.g. "CI pipeline").
 */
public record TokenName(String value) implements ValueObject<String> {
    public TokenName {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("TokenName must not be blank");
        }
    }
}
