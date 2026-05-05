package com.hlag.sourceviewer.domain.model.identifier;

/** Description text of a domain object. */
public record Description(String value) implements ValueObject<String> {
    public Description {
        if (value == null) {
            throw new IllegalArgumentException("Description must not be null");
        }
    }

    public static final Description EMPTY = new Description("");
}
