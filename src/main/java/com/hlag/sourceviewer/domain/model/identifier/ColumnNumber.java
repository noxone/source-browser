package com.hlag.sourceviewer.domain.model.identifier;

/** Column number within a line (1-based). */
public record ColumnNumber(Integer value) implements ValueObject<Integer> {
    public ColumnNumber {
        if (value == null || value < 1) {
            throw new IllegalArgumentException(
                "ColumnNumber must be >= 1, was: " + value);
        }
    }
}
