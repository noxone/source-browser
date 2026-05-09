package com.hlag.sourceviewer.infrastructure.security;

import com.hlag.sourceviewer.domain.model.identifier.PrincipalName;
import com.hlag.sourceviewer.domain.model.user.UserAccount;
import com.hlag.sourceviewer.domain.port.incoming.ManageUserAccountsUseCase;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the role-augmentation logic in {@link UserAccountSecurityAugmentor},
 * in particular the handling of service account principals (with the {@code svc:} prefix).
 */
class UserAccountSecurityAugmentorUnitTest {

    private ManageUserAccountsUseCase manageUserAccountsUseCase;
    private AuthenticationRequestContext context;
    private UserAccountSecurityAugmentor augmentor;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        manageUserAccountsUseCase = mock(ManageUserAccountsUseCase.class);
        context = mock(AuthenticationRequestContext.class);
        // Make runBlocking execute the supplier synchronously
        when(context.runBlocking(any(Supplier.class)))
                .thenAnswer(inv -> {
                    Supplier<SecurityIdentity> supplier = inv.getArgument(0);
                    return Uni.createFrom().item(supplier.get());
                });
        augmentor = new UserAccountSecurityAugmentor(manageUserAccountsUseCase);
    }

    // ── Anonymous identity ─────────────────────────────────────────────────────

    @Test
    void augment_returns_anonymous_identity_unchanged() {
        var identity = QuarkusSecurityIdentity.builder()
                .setAnonymous(true)
                .build();

        var result = augmentor.augment(identity, context)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();

        assertThat(result.isAnonymous()).isTrue();
        verifyNoInteractions(manageUserAccountsUseCase);
    }

    // ── Regular (OIDC) users ───────────────────────────────────────────────────

    @Test
    void augment_provisions_regular_user_and_adds_admin_role() {
        var identity = identityFor("alice");
        var account = userAccount("alice", true, false);
        when(manageUserAccountsUseCase.provisionUser(new PrincipalName("alice")))
                .thenReturn(account);

        var result = augment(identity);

        assertThat(result.getRoles()).contains("admin");
        verify(manageUserAccountsUseCase).provisionUser(new PrincipalName("alice"));
        verify(manageUserAccountsUseCase, never()).findUser(any());
    }

    @Test
    void augment_provisions_regular_user_without_admin_role_when_not_admin() {
        var identity = identityFor("bob");
        var account = userAccount("bob", false, false);
        when(manageUserAccountsUseCase.provisionUser(new PrincipalName("bob")))
                .thenReturn(account);

        var result = augment(identity);

        assertThat(result.getRoles()).doesNotContain("admin");
    }

    // ── Service account principals ─────────────────────────────────────────────

    @Test
    void augment_uses_findUser_not_provisionUser_for_service_account_principal() {
        var identity = identityFor("svc:my-bot");
        var account = userAccount("svc:my-bot", false, true);
        when(manageUserAccountsUseCase.findUser(new PrincipalName("svc:my-bot")))
                .thenReturn(Optional.of(account));

        augment(identity);

        verify(manageUserAccountsUseCase).findUser(new PrincipalName("svc:my-bot"));
        verify(manageUserAccountsUseCase, never()).provisionUser(any());
    }

    @Test
    void augment_adds_admin_role_for_admin_service_account() {
        var identity = identityFor("svc:admin-bot");
        var account = userAccount("svc:admin-bot", true, true);
        when(manageUserAccountsUseCase.findUser(new PrincipalName("svc:admin-bot")))
                .thenReturn(Optional.of(account));

        var result = augment(identity);

        assertThat(result.getRoles()).contains("admin");
    }

    @Test
    void augment_does_not_create_account_for_unknown_service_account_principal() {
        var identity = identityFor("svc:unknown-bot");
        when(manageUserAccountsUseCase.findUser(new PrincipalName("svc:unknown-bot")))
                .thenReturn(Optional.empty());

        var result = augment(identity);

        assertThat(result.getRoles()).doesNotContain("admin");
        verify(manageUserAccountsUseCase, never()).provisionUser(any());
    }

    // ── Error handling ─────────────────────────────────────────────────────────

    @Test
    void augment_returns_original_identity_when_provisioning_fails() {
        var identity = identityFor("error-user");
        when(manageUserAccountsUseCase.provisionUser(any()))
                .thenThrow(new RuntimeException("DB connection failed"));

        var result = augment(identity);

        assertThat(result.getPrincipal().getName()).isEqualTo("error-user");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private SecurityIdentity identityFor(String principalName) {
        return QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal(principalName))
                .build();
    }

    private UserAccount userAccount(String principalName, boolean admin, boolean serviceAccount) {
        return new UserAccount(new PrincipalName(principalName), admin, serviceAccount, Instant.now());
    }

    private SecurityIdentity augment(SecurityIdentity identity) {
        return augmentor.augment(identity, context)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem()
                .getItem();
    }
}
