package com.hlag.sourceviewer.domain.model.identifier;

/**
 * Human-readable description for a Git credential, used to distinguish multiple credentials in the UI.
 */
public record CredentialDescription(String value) implements ValueObject<String> {
    public CredentialDescription {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("CredentialDescription must not be blank");
        }
    }
}
