package com.hlag.sourceviewer.domain.model.user;

import com.hlag.sourceviewer.domain.model.identifier.PrincipalName;
import com.hlag.sourceviewer.domain.model.identifier.UserAccountIdentifier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A registered user account with an associated admin flag.
 *
 * <p>Accounts are provisioned automatically on first login. The first account
 * ever created receives admin privileges so that the system can be bootstrapped
 * without out-of-band configuration.</p>
 */
@Entity
@Table(name = "user_account")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "principal_name", nullable = false, unique = true)
    private PrincipalName principalName;

    @Column(name = "is_admin", nullable = false)
    private boolean admin;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserAccount() {}

    /**
     * Creates a new user account for the given principal.
     *
     * @param principalName the unique name of the principal from the identity provider
     * @param admin         whether this account has administrator privileges
     * @param createdAt     the instant at which the account was first provisioned
     */
    public UserAccount(PrincipalName principalName, boolean admin, Instant createdAt) {
        this.principalName = principalName;
        this.admin = admin;
        this.createdAt = createdAt;
    }

    /**
     * Returns the unique identifier of this account, or {@code null} if not yet persisted.
     */
    public UserAccountIdentifier identifier() {
        return id != null ? new UserAccountIdentifier(id) : null;
    }

    /** Returns the principal name that uniquely identifies this user within the identity provider. */
    public PrincipalName principalName() { return principalName; }

    /** Returns {@code true} if this user has administrator privileges. */
    public boolean isAdmin() { return admin; }

    /** Returns the instant at which this account was first provisioned. */
    public Instant createdAt() { return createdAt; }

    /**
     * Sets the administrator flag for this account.
     *
     * @param admin {@code true} to grant administrator privileges, {@code false} to revoke them
     */
    public void setAdmin(boolean admin) { this.admin = admin; }
}
