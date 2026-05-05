package com.hlag.sourceviewer.domain.model.identifier;

/** Unique identifier of an indexed source file. */
public record FileIdentifier(Long value) implements ValueObject<Long> {
    public FileIdentifier {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(
                "FileIdentifier must be a positive number, was: " + value);
        }
    }
}
