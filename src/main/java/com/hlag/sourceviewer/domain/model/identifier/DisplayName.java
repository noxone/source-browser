package com.hlag.sourceviewer.domain.model.identifier;

/** Display name for the user interface. */
public record DisplayName(String value) implements ValueObject<String> {
    public DisplayName {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("DisplayName must not be blank");
        }
    }
}
