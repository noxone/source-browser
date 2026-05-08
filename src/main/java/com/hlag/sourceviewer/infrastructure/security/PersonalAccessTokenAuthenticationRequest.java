package com.hlag.sourceviewer.infrastructure.security;

import io.quarkus.security.identity.request.BaseAuthenticationRequest;

/**
 * Authentication request carrying a raw personal access token value (including the {@code svt_} prefix).
 * Created by {@link PersonalAccessTokenAuthMechanism} and consumed by
 * {@link PersonalAccessTokenIdentityProvider}.
 */
public class PersonalAccessTokenAuthenticationRequest extends BaseAuthenticationRequest {

    private final String rawToken;

    /**
     * Creates a new authentication request for the given raw token.
     *
     * @param rawToken the raw {@code svt_}-prefixed token extracted from the Authorization header
     */
    public PersonalAccessTokenAuthenticationRequest(String rawToken) {
        this.rawToken = rawToken;
    }

    /**
     * Returns the raw token value.
     */
    public String rawToken() {
        return rawToken;
    }
}
