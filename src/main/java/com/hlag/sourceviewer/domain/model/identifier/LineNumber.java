package com.hlag.sourceviewer.domain.model.identifier;

/** Line number within a source file (1-based). */
public record LineNumber(Integer value) implements ValueObject<Integer> {
    public LineNumber {
        if (value == null || value < 1) {
            throw new IllegalArgumentException(
                "LineNumber must be >= 1, was: " + value);
        }
    }
}
