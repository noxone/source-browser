package com.hlag.sourceviewer.domain.model.identifier;

/** Number of tokens in a parsed file. */
public record TokenCount(Integer value) implements ValueObject<Integer> {
    public TokenCount {
        if (value == null || value < 0) {
            throw new IllegalArgumentException(
                "TokenCount must not be negative, was: " + value);
        }
    }
}
