package com.hlag.sourceviewer.domain.model.identifier;

/** Simple (unqualified) name of a Java element (e.g. "Symbol"). */
public record SimpleName(String value) implements ValueObject<String> {
    public SimpleName {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("SimpleName must not be blank");
        }
    }
}
