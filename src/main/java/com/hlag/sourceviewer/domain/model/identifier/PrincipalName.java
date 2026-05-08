package com.hlag.sourceviewer.domain.model.identifier;

/**
 * Username / principal name from the identity provider (OIDC subject or preferred_username claim).
 */
public record PrincipalName(String value) implements ValueObject<String> {
    public PrincipalName {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("PrincipalName must not be blank");
        }
    }
}
