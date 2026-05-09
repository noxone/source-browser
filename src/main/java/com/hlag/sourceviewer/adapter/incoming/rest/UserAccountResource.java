package com.hlag.sourceviewer.adapter.incoming.rest;

import com.hlag.sourceviewer.adapter.incoming.rest.dto.UpdateUserAccountDto;
import com.hlag.sourceviewer.adapter.incoming.rest.dto.UserAccountDto;
import com.hlag.sourceviewer.adapter.incoming.rest.dto.UserAccountPageDto;
import com.hlag.sourceviewer.domain.model.identifier.PrincipalName;
import com.hlag.sourceviewer.domain.model.identifier.UserAccountIdentifier;
import com.hlag.sourceviewer.domain.model.user.UserAccount;
import com.hlag.sourceviewer.domain.port.incoming.ManageUserAccountsUseCase;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

/**
 * REST resource for managing user accounts and querying the current user's profile.
 */
@Authenticated
@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserAccountResource {

    private final ManageUserAccountsUseCase manageUserAccountsUseCase;

    @Inject
    public UserAccountResource(ManageUserAccountsUseCase manageUserAccountsUseCase) {
        this.manageUserAccountsUseCase = manageUserAccountsUseCase;
    }

    /**
     * Returns the account of the currently authenticated user.
     *
     * @param identity the injected security identity of the caller
     * @return the caller's account
     * @throws NotFoundException if the account has not been provisioned yet
     */
    @GET
    @Path("/me")
    public UserAccountDto getCurrentUser(@Context SecurityIdentity identity) {
        var principalName = new PrincipalName(identity.getPrincipal().getName());
        return manageUserAccountsUseCase.findUser(principalName)
                .map(this::toDto)
                .orElseThrow(() -> new NotFoundException("User account not found"));
    }

    /**
     * Returns a page of provisioned user accounts. Requires administrator privileges.
     *
     * @param query    optional substring to filter by principal name (case-insensitive), defaults to empty
     * @param page     the one-based page number to return, defaults to 1
     * @param pageSize the maximum number of accounts per page, defaults to 25
     * @return a page of matching user accounts
     */
    @GET
    @RolesAllowed("admin")
    public UserAccountPageDto listUsers(
            @QueryParam("query") @DefaultValue("") String query,
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("pageSize") @DefaultValue("25") int pageSize) {
        var domainPage = manageUserAccountsUseCase.listUsers(query, page, pageSize);
        return new UserAccountPageDto(
                domainPage.items().stream().map(this::toDto).toList(),
                domainPage.totalItems(),
                domainPage.page(),
                domainPage.pageSize(),
                domainPage.totalPages()
        );
    }

    /**
     * Updates the administrator flag of the account with the given identifier.
     * Requires administrator privileges.
     *
     * @param id      the account identifier
     * @param request the updated field values
     * @return the updated account
     * @throws NotFoundException if no account with the given identifier exists
     */
    @PUT
    @Path("/{id}")
    @RolesAllowed("admin")
    public UserAccountDto updateUser(@PathParam("id") Long id, UpdateUserAccountDto request) {
        try {
            var updated = manageUserAccountsUseCase.setAdminStatus(
                    new UserAccountIdentifier(id), request.admin());
            return toDto(updated);
        } catch (java.util.NoSuchElementException e) {
            throw new NotFoundException("User account not found: " + id);
        }
    }

    private UserAccountDto toDto(UserAccount account) {
        return new UserAccountDto(
                account.identifier().value(),
                account.principalName().value(),
                account.isAdmin(),
                account.isServiceAccount(),
                account.createdAt().toString()
        );
    }
}
