package com.hlag.sourceviewer.domain.model.identifier;

/** Number of lines in a source file. */
public record LineCount(Integer value) implements ValueObject<Integer> {
    public LineCount {
        if (value == null || value < 0) {
            throw new IllegalArgumentException(
                "LineCount must not be negative, was: " + value);
        }
    }
}
