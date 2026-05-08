package com.hlag.sourceviewer.infrastructure.security;

import com.hlag.sourceviewer.domain.model.identifier.PrincipalName;
import com.hlag.sourceviewer.domain.port.incoming.ManageUserAccountsUseCase;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Augments the authenticated {@link SecurityIdentity} with application-level roles.
 *
 * <p>After every successful authentication (OIDC or PAT), this augmentor:
 * <ul>
 *   <li>Looks up or creates the user's {@code user_account} record.</li>
 *   <li>Adds the {@code "admin"} role to the identity if the account has admin privileges.</li>
 * </ul>
 * The first user ever provisioned is automatically made an administrator so the system
 * can be bootstrapped without any out-of-band configuration.</p>
 */
@ApplicationScoped
public class UserAccountSecurityAugmentor implements SecurityIdentityAugmentor {

    private static final Logger logger = LoggerFactory.getLogger(UserAccountSecurityAugmentor.class);

    private final ManageUserAccountsUseCase manageUserAccountsUseCase;

    @Inject
    public UserAccountSecurityAugmentor(ManageUserAccountsUseCase manageUserAccountsUseCase) {
        this.manageUserAccountsUseCase = manageUserAccountsUseCase;
    }

    /** @inheritDoc */
    @Override
    public Uni<SecurityIdentity> augment(
            SecurityIdentity identity,
            AuthenticationRequestContext context) {

        if (identity.isAnonymous()) {
            return Uni.createFrom().item(identity);
        }

        return context.runBlocking(() -> augmentWithRoles(identity));
    }

    private SecurityIdentity augmentWithRoles(SecurityIdentity identity) {
        try {
            var principalName = new PrincipalName(identity.getPrincipal().getName());
            var account = manageUserAccountsUseCase.provisionUser(principalName);

            if (!account.isAdmin()) {
                return identity;
            }

            return QuarkusSecurityIdentity.builder(identity)
                    .addRole("admin")
                    .build();
        } catch (Exception exception) {
            logger.warn("Failed to augment security identity for '{}': {}",
                    identity.getPrincipal().getName(), exception.getMessage());
            return identity;
        }
    }
}
