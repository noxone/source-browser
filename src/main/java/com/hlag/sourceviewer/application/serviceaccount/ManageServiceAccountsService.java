package com.hlag.sourceviewer.application.serviceaccount;

import com.hlag.sourceviewer.domain.model.identifier.PersonalAccessTokenIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.PrincipalName;
import com.hlag.sourceviewer.domain.model.identifier.UserAccountIdentifier;
import com.hlag.sourceviewer.domain.model.token.PersonalAccessToken;
import com.hlag.sourceviewer.domain.model.user.UserAccount;
import com.hlag.sourceviewer.domain.port.incoming.ManagePersonalAccessTokensUseCase;
import com.hlag.sourceviewer.domain.port.incoming.ManageServiceAccountsUseCase;
import com.hlag.sourceviewer.domain.port.outgoing.PersonalAccessTokenStore;
import com.hlag.sourceviewer.domain.port.outgoing.UserAccountStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Application service for creating and managing service accounts and their personal access tokens.
 */
@ApplicationScoped
public class ManageServiceAccountsService implements ManageServiceAccountsUseCase {

    private static final Pattern VALID_NAME_PATTERN = Pattern.compile("[a-zA-Z0-9_-]{1,64}");

    private static final Logger logger = LoggerFactory.getLogger(ManageServiceAccountsService.class);

    private final UserAccountStore userAccountStore;
    private final PersonalAccessTokenStore tokenStore;
    private final ManagePersonalAccessTokensUseCase manageTokensUseCase;

    @Inject
    public ManageServiceAccountsService(
            UserAccountStore userAccountStore,
            PersonalAccessTokenStore tokenStore,
            ManagePersonalAccessTokensUseCase manageTokensUseCase) {
        this.userAccountStore = userAccountStore;
        this.tokenStore = tokenStore;
        this.manageTokensUseCase = manageTokensUseCase;
    }

    /** @inheritDoc */
    @Override
    @Transactional
    public UserAccount createServiceAccount(CreateServiceAccountCommand command) {
        validateName(command.name());

        var principalName = new PrincipalName(SERVICE_ACCOUNT_PRINCIPAL_PREFIX + command.name());
        if (userAccountStore.findByPrincipalName(principalName).isPresent()) {
            throw new IllegalArgumentException(
                    "A service account with name '" + command.name() + "' already exists");
        }

        var account = new UserAccount(principalName, command.admin(), true, Instant.now());
        userAccountStore.insert(account);
        logger.info("Service account '{}' created (admin={})", command.name(), command.admin());
        return account;
    }

    /** @inheritDoc */
    @Override
    public List<UserAccount> listServiceAccounts() {
        return userAccountStore.findAll().stream()
                .filter(UserAccount::isServiceAccount)
                .toList();
    }

    /** @inheritDoc */
    @Override
    public Optional<UserAccount> findServiceAccount(UserAccountIdentifier identifier) {
        return userAccountStore.findById(identifier)
                .filter(UserAccount::isServiceAccount);
    }

    /** @inheritDoc */
    @Override
    @Transactional
    public UserAccount setAdminStatus(UserAccountIdentifier identifier, boolean admin) {
        var account = requireServiceAccount(identifier);
        account.setAdmin(admin);
        logger.info("Admin status of service account {} set to {}", identifier.value(), admin);
        return account;
    }

    /** @inheritDoc */
    @Override
    @Transactional
    public void deleteServiceAccount(UserAccountIdentifier identifier) {
        var account = userAccountStore.findById(identifier)
                .filter(UserAccount::isServiceAccount);
        if (account.isEmpty()) {
            return;
        }
        tokenStore.deleteAllByOwner(account.get().principalName());
        userAccountStore.deleteById(identifier);
        logger.info("Service account {} and all its tokens deleted", identifier.value());
    }

    /** @inheritDoc */
    @Override
    public List<PersonalAccessToken> listTokens(UserAccountIdentifier identifier) {
        var account = requireServiceAccount(identifier);
        return tokenStore.findByOwner(account.principalName());
    }

    /** @inheritDoc */
    @Override
    @Transactional
    public ManagePersonalAccessTokensUseCase.TokenCreationResult createToken(
            UserAccountIdentifier identifier, CreateTokenCommand command) {
        var account = requireServiceAccount(identifier);
        var delegatedCommand = new ManagePersonalAccessTokensUseCase.CreateTokenCommand(
                account.principalName(),
                command.name(),
                command.expiresAt());
        return manageTokensUseCase.createToken(delegatedCommand);
    }

    /** @inheritDoc */
    @Override
    @Transactional
    public void revokeToken(PersonalAccessTokenIdentifier tokenIdentifier) {
        tokenStore.deleteById(tokenIdentifier);
        logger.info("Token {} revoked by administrator", tokenIdentifier.value());
    }

    private UserAccount requireServiceAccount(UserAccountIdentifier identifier) {
        return userAccountStore.findById(identifier)
                .filter(UserAccount::isServiceAccount)
                .orElseThrow(() -> new NoSuchElementException(
                        "Service account not found: " + identifier.value()));
    }

    private void validateName(String name) {
        if (name == null || !VALID_NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "Service account name must be 1–64 characters long and contain only "
                            + "letters, digits, hyphens, or underscores");
        }
    }
}
