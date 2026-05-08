package com.hlag.sourceviewer.infrastructure.security;

import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.Optional;
import java.util.Set;

/**
 * Custom HTTP authentication mechanism that handles personal access tokens.
 *
 * <p>Only intercepts requests carrying a Bearer token with the {@code svt_} prefix;
 * all other Bearer tokens (JWTs from the OIDC provider) are ignored so that the
 * standard Quarkus OIDC bearer mechanism can process them.</p>
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class PersonalAccessTokenAuthMechanism implements HttpAuthenticationMechanism {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String PAT_PREFIX = "svt_";

    /** @inheritDoc */
    @Override
    public Uni<SecurityIdentity> authenticate(
            RoutingContext context,
            IdentityProviderManager identityProviderManager) {

        String authHeader = context.request().getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return Uni.createFrom().optional(Optional.empty());
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        if (!token.startsWith(PAT_PREFIX)) {
            // Not a PAT — defer to the OIDC mechanism
            return Uni.createFrom().optional(Optional.empty());
        }

        return identityProviderManager.authenticate(
                new PersonalAccessTokenAuthenticationRequest(token));
    }

    /** @inheritDoc */
    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        return Uni.createFrom().item(
                new ChallengeData(401, "WWW-Authenticate", "Bearer realm=\"sourceviewer\""));
    }

    /** @inheritDoc */
    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        return Set.of(PersonalAccessTokenAuthenticationRequest.class);
    }

    /** @inheritDoc */
    @Override
    public Uni<HttpCredentialTransport> getCredentialTransport(RoutingContext context) {
        return Uni.createFrom().item(
                new HttpCredentialTransport(HttpCredentialTransport.Type.AUTHORIZATION, "Bearer"));
    }
}
