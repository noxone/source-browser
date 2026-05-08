package com.hlag.sourceviewer.infrastructure.security;

import com.hlag.sourceviewer.application.token.ManagePersonalAccessTokensService;
import com.hlag.sourceviewer.domain.model.identifier.TokenHash;
import com.hlag.sourceviewer.domain.port.outgoing.PersonalAccessTokenStore;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Set;

/**
 * Validates personal access tokens by looking up their SHA-256 hash in the database.
 * Runs on a blocking worker thread via {@link AuthenticationRequestContext#runBlocking}.
 */
@ApplicationScoped
public class PersonalAccessTokenIdentityProvider
        implements IdentityProvider<PersonalAccessTokenAuthenticationRequest> {

    private final PersonalAccessTokenStore tokenStore;

    @Inject
    public PersonalAccessTokenIdentityProvider(PersonalAccessTokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    /** @inheritDoc */
    @Override
    public Class<PersonalAccessTokenAuthenticationRequest> getRequestType() {
        return PersonalAccessTokenAuthenticationRequest.class;
    }

    /** @inheritDoc */
    @Override
    public Uni<SecurityIdentity> authenticate(
            PersonalAccessTokenAuthenticationRequest request,
            AuthenticationRequestContext context) {
        return context.runBlocking(() -> validateToken(request.rawToken()));
    }

    private SecurityIdentity validateToken(String rawToken) {
        String hash = ManagePersonalAccessTokensService.sha256Hex(rawToken);
        var token = tokenStore.findByTokenHash(new TokenHash(hash));

        if (token.isEmpty() || token.get().isExpired()) {
            return null;
        }

        return QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal(token.get().owner().value()))
                .addRoles(Set.of("user"))
                .build();
    }
}
