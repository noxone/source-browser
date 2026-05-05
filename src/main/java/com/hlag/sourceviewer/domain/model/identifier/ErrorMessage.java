package com.hlag.sourceviewer.domain.model.identifier;

/** Error message from a failed operation. */
public record ErrorMessage(String value) implements ValueObject<String> {
    public ErrorMessage {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ErrorMessage must not be blank");
        }
    }
}
