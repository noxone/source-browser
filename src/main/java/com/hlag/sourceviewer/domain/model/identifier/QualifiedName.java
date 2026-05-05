package com.hlag.sourceviewer.domain.model.identifier;

/** Fully qualified name of a Java element (e.g. "com.hlag.sourceviewer.domain.model.Symbol"). */
public record QualifiedName(String value) implements ValueObject<String> {
    public QualifiedName {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("QualifiedName must not be blank");
        }
    }
}
