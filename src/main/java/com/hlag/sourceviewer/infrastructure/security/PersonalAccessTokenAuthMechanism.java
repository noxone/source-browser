package com.hlag.sourceviewer.infrastructure.security;

import io.quarkus.oidc.runtime.OidcAuthenticationMechanism;
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
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Custom HTTP authentication mechanism that handles personal access tokens.
 *
 * <p>Declared {@code @Alternative @Priority(1)} so it is selected over the built-in OIDC
 * mechanism. Requests carrying a {@code svt_}-prefixed Bearer token are authenticated as
 * PATs; all other requests (JWT Bearer tokens, no Authorization header) are forwarded to
 * {@link OidcAuthenticationMechanism} so that normal OIDC login continues to work.</p>
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class PersonalAccessTokenAuthMechanism implements HttpAuthenticationMechanism {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String PAT_PREFIX = "svt_";

    /**
     * Optional because OIDC is disabled in the test profile
     * ({@code %test.quarkus.oidc.enabled=false}).
     */
    @Inject
    @Any
    Instance<OidcAuthenticationMechanism> oidcMechanism;

    /** @inheritDoc */
    @Override
    public Uni<SecurityIdentity> authenticate(
            RoutingContext context,
            IdentityProviderManager identityProviderManager) {

        String authHeader = context.request().getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length());
            if (token.startsWith(PAT_PREFIX)) {
                return identityProviderManager.authenticate(
                        new PersonalAccessTokenAuthenticationRequest(token));
            }
        }

        // Not a PAT — delegate to the OIDC bearer mechanism
        return oidc().map(m -> m.authenticate(context, identityProviderManager))
                .orElseGet(() -> Uni.createFrom().optional(Optional.empty()));
    }

    /** @inheritDoc */
    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        return oidc().map(m -> m.getChallenge(context))
                .orElseGet(() -> Uni.createFrom().item(
                        new ChallengeData(401, "WWW-Authenticate", "Bearer realm=\"sourceviewer\"")));
    }

    /** @inheritDoc */
    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        Set<Class<? extends AuthenticationRequest>> types = new HashSet<>();
        types.add(PersonalAccessTokenAuthenticationRequest.class);
        oidc().ifPresent(m -> types.addAll(m.getCredentialTypes()));
        return types;
    }

    /** @inheritDoc */
    @Override
    public Uni<HttpCredentialTransport> getCredentialTransport(RoutingContext context) {
        return oidc().map(m -> m.getCredentialTransport(context))
                .orElseGet(() -> Uni.createFrom().item(
                        new HttpCredentialTransport(HttpCredentialTransport.Type.AUTHORIZATION, "Bearer")));
    }

    private Optional<OidcAuthenticationMechanism> oidc() {
        return oidcMechanism.isUnsatisfied() ? Optional.empty() : Optional.of(oidcMechanism.get());
    }
}
