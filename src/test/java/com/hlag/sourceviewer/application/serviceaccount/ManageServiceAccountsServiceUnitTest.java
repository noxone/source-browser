package com.hlag.sourceviewer.application.serviceaccount;

import com.hlag.sourceviewer.domain.model.identifier.PersonalAccessTokenIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.PrincipalName;
import com.hlag.sourceviewer.domain.model.identifier.TokenName;
import com.hlag.sourceviewer.domain.model.identifier.UserAccountIdentifier;
import com.hlag.sourceviewer.domain.model.token.PersonalAccessToken;
import com.hlag.sourceviewer.domain.model.user.UserAccount;
import com.hlag.sourceviewer.domain.port.incoming.ManagePersonalAccessTokensUseCase;
import com.hlag.sourceviewer.domain.port.incoming.ManagePersonalAccessTokensUseCase.CreateTokenCommand;
import com.hlag.sourceviewer.domain.port.incoming.ManagePersonalAccessTokensUseCase.TokenCreationResult;
import com.hlag.sourceviewer.domain.port.incoming.ManageServiceAccountsUseCase;
import com.hlag.sourceviewer.domain.port.incoming.ManageServiceAccountsUseCase.CreateServiceAccountCommand;
import com.hlag.sourceviewer.domain.port.outgoing.PersonalAccessTokenStore;
import com.hlag.sourceviewer.domain.port.outgoing.UserAccountStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ManageServiceAccountsServiceUnitTest {

    private UserAccountStore userAccountStore;
    private PersonalAccessTokenStore tokenStore;
    private ManagePersonalAccessTokensUseCase manageTokensUseCase;
    private ManageServiceAccountsService service;

    @BeforeEach
    void setUp() {
        userAccountStore = mock(UserAccountStore.class);
        tokenStore = mock(PersonalAccessTokenStore.class);
        manageTokensUseCase = mock(ManagePersonalAccessTokensUseCase.class);
        service = new ManageServiceAccountsService(userAccountStore, tokenStore, manageTokensUseCase);
    }

    // ── createServiceAccount ──────────────────────────────────────────────────

    @Test
    void createServiceAccount_stores_account_with_svc_prefix() {
        when(userAccountStore.findByPrincipalName(any())).thenReturn(Optional.empty());

        service.createServiceAccount(new CreateServiceAccountCommand("my-bot", false));

        verify(userAccountStore).insert(argThat(account ->
                account.principalName().value().equals("svc:my-bot")
                        && account.isServiceAccount()
                        && !account.isAdmin()
        ));
    }

    @Test
    void createServiceAccount_stores_admin_flag() {
        when(userAccountStore.findByPrincipalName(any())).thenReturn(Optional.empty());

        service.createServiceAccount(new CreateServiceAccountCommand("ci-admin", true));

        verify(userAccountStore).insert(argThat(account ->
                account.isAdmin() && account.isServiceAccount()
        ));
    }

    @Test
    void createServiceAccount_throws_when_name_already_taken() {
        var existing = serviceAccount("existing-bot", false);
        when(userAccountStore.findByPrincipalName(new PrincipalName("svc:existing-bot")))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() ->
                service.createServiceAccount(new CreateServiceAccountCommand("existing-bot", false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createServiceAccount_throws_for_blank_name() {
        assertThatThrownBy(() ->
                service.createServiceAccount(new CreateServiceAccountCommand("", false)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createServiceAccount_throws_for_name_with_spaces() {
        assertThatThrownBy(() ->
                service.createServiceAccount(new CreateServiceAccountCommand("my bot", false)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createServiceAccount_throws_for_name_exceeding_64_characters() {
        var longName = "a".repeat(65);
        assertThatThrownBy(() ->
                service.createServiceAccount(new CreateServiceAccountCommand(longName, false)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createServiceAccount_accepts_name_with_hyphens_and_underscores() {
        when(userAccountStore.findByPrincipalName(any())).thenReturn(Optional.empty());

        assertThatNoException().isThrownBy(() ->
                service.createServiceAccount(new CreateServiceAccountCommand("my-ci_bot-1", false)));
    }

    // ── listServiceAccounts ───────────────────────────────────────────────────

    @Test
    void listServiceAccounts_returns_only_service_accounts() {
        var serviceAccount = serviceAccount("bot", false);
        var humanUser = humanUser("alice");
        when(userAccountStore.findAll()).thenReturn(List.of(serviceAccount, humanUser));

        var result = service.listServiceAccounts();

        assertThat(result).containsExactly(serviceAccount);
    }

    @Test
    void listServiceAccounts_returns_empty_list_when_none_exist() {
        when(userAccountStore.findAll()).thenReturn(List.of());

        assertThat(service.listServiceAccounts()).isEmpty();
    }

    // ── findServiceAccount ────────────────────────────────────────────────────

    @Test
    void findServiceAccount_returns_account_when_found() {
        var identifier = new UserAccountIdentifier(1L);
        var account = serviceAccount("bot", false);
        when(userAccountStore.findById(identifier)).thenReturn(Optional.of(account));

        assertThat(service.findServiceAccount(identifier)).contains(account);
    }

    @Test
    void findServiceAccount_returns_empty_when_account_is_human_user() {
        var identifier = new UserAccountIdentifier(1L);
        when(userAccountStore.findById(identifier)).thenReturn(Optional.of(humanUser("alice")));

        assertThat(service.findServiceAccount(identifier)).isEmpty();
    }

    @Test
    void findServiceAccount_returns_empty_when_not_found() {
        when(userAccountStore.findById(any())).thenReturn(Optional.empty());

        assertThat(service.findServiceAccount(new UserAccountIdentifier(99L))).isEmpty();
    }

    // ── setAdminStatus ────────────────────────────────────────────────────────

    @Test
    void setAdminStatus_grants_admin_privileges() {
        var identifier = new UserAccountIdentifier(1L);
        var account = serviceAccount("bot", false);
        when(userAccountStore.findById(identifier)).thenReturn(Optional.of(account));

        var result = service.setAdminStatus(identifier, true);

        assertThat(result.isAdmin()).isTrue();
    }

    @Test
    void setAdminStatus_revokes_admin_privileges() {
        var identifier = new UserAccountIdentifier(1L);
        var account = serviceAccount("bot", true);
        when(userAccountStore.findById(identifier)).thenReturn(Optional.of(account));

        var result = service.setAdminStatus(identifier, false);

        assertThat(result.isAdmin()).isFalse();
    }

    @Test
    void setAdminStatus_throws_when_not_found() {
        when(userAccountStore.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setAdminStatus(new UserAccountIdentifier(99L), true))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ── deleteServiceAccount ──────────────────────────────────────────────────

    @Test
    void deleteServiceAccount_removes_all_tokens_then_account() {
        var identifier = new UserAccountIdentifier(1L);
        var account = serviceAccount("bot", false);
        when(userAccountStore.findById(identifier)).thenReturn(Optional.of(account));

        service.deleteServiceAccount(identifier);

        var inOrder = inOrder(tokenStore, userAccountStore);
        inOrder.verify(tokenStore).deleteAllByOwner(account.principalName());
        inOrder.verify(userAccountStore).deleteById(identifier);
    }

    @Test
    void deleteServiceAccount_does_nothing_when_not_found() {
        when(userAccountStore.findById(any())).thenReturn(Optional.empty());

        assertThatNoException().isThrownBy(() ->
                service.deleteServiceAccount(new UserAccountIdentifier(99L)));

        verifyNoInteractions(tokenStore);
        verify(userAccountStore, never()).deleteById(any());
    }

    @Test
    void deleteServiceAccount_does_nothing_for_human_user() {
        var identifier = new UserAccountIdentifier(1L);
        when(userAccountStore.findById(identifier)).thenReturn(Optional.of(humanUser("alice")));

        service.deleteServiceAccount(identifier);

        verifyNoInteractions(tokenStore);
        verify(userAccountStore, never()).deleteById(any());
    }

    // ── listTokens ────────────────────────────────────────────────────────────

    @Test
    void listTokens_returns_tokens_for_service_account() {
        var identifier = new UserAccountIdentifier(1L);
        var account = serviceAccount("bot", false);
        var token = pat(account.principalName(), "ci-token");
        when(userAccountStore.findById(identifier)).thenReturn(Optional.of(account));
        when(tokenStore.findByOwner(account.principalName())).thenReturn(List.of(token));

        var result = service.listTokens(identifier);

        assertThat(result).containsExactly(token);
    }

    @Test
    void listTokens_throws_when_service_account_not_found() {
        when(userAccountStore.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listTokens(new UserAccountIdentifier(99L)))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ── createToken ───────────────────────────────────────────────────────────

    @Test
    void createToken_delegates_to_token_use_case_with_service_account_as_owner() {
        var identifier = new UserAccountIdentifier(1L);
        var account = serviceAccount("bot", false);
        when(userAccountStore.findById(identifier)).thenReturn(Optional.of(account));

        var token = pat(account.principalName(), "deploy-token");
        var expected = new TokenCreationResult(token, "svt_raw");
        when(manageTokensUseCase.createToken(any())).thenReturn(expected);

        var command = new ManageServiceAccountsUseCase.CreateTokenCommand(
                new TokenName("deploy-token"), Optional.empty());
        var result = service.createToken(identifier, command);

        assertThat(result.rawToken()).isEqualTo("svt_raw");
        verify(manageTokensUseCase).createToken(argThat(c ->
                c.owner().value().equals("svc:bot")
                        && c.name().value().equals("deploy-token")
        ));
    }

    @Test
    void createToken_throws_when_service_account_not_found() {
        when(userAccountStore.findById(any())).thenReturn(Optional.empty());

        var command = new ManageServiceAccountsUseCase.CreateTokenCommand(
                new TokenName("token"), Optional.empty());
        assertThatThrownBy(() -> service.createToken(new UserAccountIdentifier(99L), command))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ── revokeToken ───────────────────────────────────────────────────────────

    @Test
    void revokeToken_delegates_to_token_store_without_owner_check() {
        var tokenId = new PersonalAccessTokenIdentifier(42L);

        service.revokeToken(tokenId);

        verify(tokenStore).deleteById(tokenId);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private UserAccount serviceAccount(String name, boolean admin) {
        return new UserAccount(new PrincipalName("svc:" + name), admin, true, Instant.now());
    }

    private UserAccount humanUser(String principalName) {
        return new UserAccount(new PrincipalName(principalName), false, false, Instant.now());
    }

    private PersonalAccessToken pat(PrincipalName owner, String name) {
        return new PersonalAccessToken(owner, new TokenName(name),
                new com.hlag.sourceviewer.domain.model.identifier.TokenHash("a".repeat(64)),
                Instant.now(), Optional.empty());
    }
}
