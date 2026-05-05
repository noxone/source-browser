package com.hlag.sourceviewer.domain.model.identifier;

/** Unique identifier of a symbol (declaration in source code). */
public record SymbolIdentifier(Long value) implements ValueObject<Long> {
    public SymbolIdentifier {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(
                "SymbolIdentifier must be a positive number, was: " + value);
        }
    }
}
