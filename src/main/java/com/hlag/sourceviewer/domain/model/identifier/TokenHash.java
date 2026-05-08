package com.hlag.sourceviewer.domain.model.identifier;

/**
 * SHA-256 hex digest of a personal access token, used for DB storage and lookup.
 */
public record TokenHash(String value) implements ValueObject<String> {
    public TokenHash {
        if (value == null || value.length() != 64) {
            throw new IllegalArgumentException("TokenHash must be a 64-character hex string");
        }
    }
}
