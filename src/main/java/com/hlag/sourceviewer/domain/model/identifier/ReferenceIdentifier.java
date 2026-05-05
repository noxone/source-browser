package com.hlag.sourceviewer.domain.model.identifier;

/** Unique identifier of a symbol reference (usage in source code). */
public record ReferenceIdentifier(Long value) implements ValueObject<Long> {
    public ReferenceIdentifier {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(
                "ReferenceIdentifier must be a positive number, was: " + value);
        }
    }
}
