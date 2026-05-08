package com.hlag.sourceviewer.application.user;

import com.hlag.sourceviewer.domain.model.identifier.PrincipalName;
import com.hlag.sourceviewer.domain.model.identifier.UserAccountIdentifier;
import com.hlag.sourceviewer.domain.model.user.UserAccount;
import com.hlag.sourceviewer.domain.port.incoming.ManageUserAccountsUseCase;
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

/**
 * Application service for provisioning and managing user accounts.
 */
@ApplicationScoped
public class ManageUserAccountsService implements ManageUserAccountsUseCase {

    private static final Logger logger = LoggerFactory.getLogger(ManageUserAccountsService.class);

    private final UserAccountStore userAccountStore;

    @Inject
    public ManageUserAccountsService(UserAccountStore userAccountStore) {
        this.userAccountStore = userAccountStore;
    }

    /** @inheritDoc */
    @Override
    @Transactional
    public UserAccount provisionUser(PrincipalName principalName) {
        var existing = userAccountStore.findByPrincipalName(principalName);
        if (existing.isPresent()) {
            return existing.get();
        }

        boolean isFirstUser = userAccountStore.countAll() == 0;
        var account = new UserAccount(principalName, isFirstUser, Instant.now());
        userAccountStore.insert(account);

        if (isFirstUser) {
            logger.info("First user '{}' provisioned as administrator", principalName.value());
        } else {
            logger.info("User '{}' provisioned", principalName.value());
        }

        return account;
    }

    /** @inheritDoc */
    @Override
    public Optional<UserAccount> findUser(PrincipalName principalName) {
        return userAccountStore.findByPrincipalName(principalName);
    }

    /** @inheritDoc */
    @Override
    public List<UserAccount> listUsers() {
        return userAccountStore.findAll();
    }

    /** @inheritDoc */
    @Override
    @Transactional
    public UserAccount setAdminStatus(UserAccountIdentifier identifier, boolean admin) {
        var account = userAccountStore.findById(identifier)
                .orElseThrow(() -> new NoSuchElementException(
                        "User account not found: " + identifier.value()));
        account.setAdmin(admin);
        logger.info("Admin status of user account {} set to {}", identifier.value(), admin);
        return account;
    }
}
