package com.hlag.sourceviewer.adapter.incoming.rest;

import com.hlag.sourceviewer.adapter.incoming.rest.dto.CreateServiceAccountDto;
import com.hlag.sourceviewer.adapter.incoming.rest.dto.CreateServiceAccountTokenDto;
import com.hlag.sourceviewer.adapter.incoming.rest.dto.PersonalAccessTokenCreatedDto;
import com.hlag.sourceviewer.adapter.incoming.rest.dto.PersonalAccessTokenDto;
import com.hlag.sourceviewer.adapter.incoming.rest.dto.ServiceAccountDto;
import com.hlag.sourceviewer.adapter.incoming.rest.dto.UpdateServiceAccountDto;
import com.hlag.sourceviewer.domain.model.identifier.PersonalAccessTokenIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.TokenName;
import com.hlag.sourceviewer.domain.model.identifier.UserAccountIdentifier;
import com.hlag.sourceviewer.domain.model.token.PersonalAccessToken;
import com.hlag.sourceviewer.domain.model.user.UserAccount;
import com.hlag.sourceviewer.domain.port.incoming.ManageServiceAccountsUseCase;
import com.hlag.sourceviewer.domain.port.incoming.ManageServiceAccountsUseCase.CreateServiceAccountCommand;
import com.hlag.sourceviewer.domain.port.incoming.ManageServiceAccountsUseCase.CreateTokenCommand;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * REST resource for managing service accounts and their personal access tokens.
 * All endpoints require administrator privileges.
 */
@Path("/api/admin/service-accounts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class ServiceAccountResource {

    private static final Logger logger = LoggerFactory.getLogger(ServiceAccountResource.class);

    private final ManageServiceAccountsUseCase manageServiceAccountsUseCase;

    @Inject
    public ServiceAccountResource(ManageServiceAccountsUseCase manageServiceAccountsUseCase) {
        this.manageServiceAccountsUseCase = manageServiceAccountsUseCase;
    }

    /**
     * Returns all service accounts ordered by creation date ascending.
     *
     * @return list of service account DTOs
     */
    @GET
    public List<ServiceAccountDto> listServiceAccounts() {
        return manageServiceAccountsUseCase.listServiceAccounts().stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Creates a new service account.
     *
     * @param request  the creation parameters
     * @param uriInfo  used to build the Location header
     * @return 201 Created with the created service account
     */
    @POST
    public Response createServiceAccount(CreateServiceAccountDto request, @Context UriInfo uriInfo) {
        logger.info("Creating service account '{}'", request.name());
        try {
            var account = manageServiceAccountsUseCase.createServiceAccount(
                    new CreateServiceAccountCommand(request.name(), request.admin()));
            URI location = uriInfo.getAbsolutePathBuilder()
                    .path(String.valueOf(account.identifier().value()))
                    .build();
            return Response.created(location).entity(toDto(account)).build();
        } catch (IllegalArgumentException exception) {
            return Response.status(Response.Status.CONFLICT).entity(exception.getMessage()).build();
        }
    }

    /**
     * Updates the administrator flag of the service account with the given identifier.
     *
     * @param id      the account identifier
     * @param request the updated field values
     * @return the updated service account
     * @throws NotFoundException if no service account with the given identifier exists
     */
    @PUT
    @Path("/{id}")
    public ServiceAccountDto updateServiceAccount(@PathParam("id") Long id, UpdateServiceAccountDto request) {
        try {
            var updated = manageServiceAccountsUseCase.setAdminStatus(
                    new UserAccountIdentifier(id), request.admin());
            return toDto(updated);
        } catch (NoSuchElementException exception) {
            throw new NotFoundException("Service account not found: " + id);
        }
    }

    /**
     * Deletes the service account with the given identifier and all of its personal access tokens.
     *
     * @param id the account identifier
     * @return 204 No Content
     */
    @DELETE
    @Path("/{id}")
    public Response deleteServiceAccount(@PathParam("id") Long id) {
        manageServiceAccountsUseCase.deleteServiceAccount(new UserAccountIdentifier(id));
        return Response.noContent().build();
    }

    /**
     * Returns all personal access tokens belonging to the service account with the given identifier.
     *
     * @param id the service account identifier
     * @return list of token metadata records
     * @throws NotFoundException if no service account with the given identifier exists
     */
    @GET
    @Path("/{id}/tokens")
    public List<PersonalAccessTokenDto> listTokens(@PathParam("id") Long id) {
        try {
            return manageServiceAccountsUseCase.listTokens(new UserAccountIdentifier(id)).stream()
                    .map(this::toTokenDto)
                    .toList();
        } catch (NoSuchElementException exception) {
            throw new NotFoundException("Service account not found: " + id);
        }
    }

    /**
     * Creates a new personal access token for the service account with the given identifier.
     * The raw token value is returned once in the response and cannot be retrieved again.
     *
     * @param id      the service account identifier
     * @param request the creation parameters
     * @param uriInfo used to build the Location header
     * @return 201 Created with the token metadata and the one-time raw token value
     * @throws NotFoundException if no service account with the given identifier exists
     */
    @POST
    @Path("/{id}/tokens")
    public Response createToken(
            @PathParam("id") Long id,
            CreateServiceAccountTokenDto request,
            @Context UriInfo uriInfo) {
        try {
            var command = new CreateTokenCommand(
                    new TokenName(request.name()),
                    Optional.ofNullable(request.expiresAt()).map(Instant::parse));
            var result = manageServiceAccountsUseCase.createToken(new UserAccountIdentifier(id), command);
            URI location = uriInfo.getAbsolutePathBuilder()
                    .path(String.valueOf(result.token().identifier().value()))
                    .build();
            return Response.created(location)
                    .entity(new PersonalAccessTokenCreatedDto(toTokenDto(result.token()), result.rawToken()))
                    .build();
        } catch (NoSuchElementException exception) {
            throw new NotFoundException("Service account not found: " + id);
        }
    }

    /**
     * Revokes the personal access token with the given identifier from the service account.
     *
     * @param id      the service account identifier (used for URL structure; not validated against token ownership)
     * @param tokenId the token identifier
     * @return 204 No Content
     */
    @DELETE
    @Path("/{id}/tokens/{tokenId}")
    public Response revokeToken(@PathParam("id") Long id, @PathParam("tokenId") Long tokenId) {
        manageServiceAccountsUseCase.revokeToken(new PersonalAccessTokenIdentifier(tokenId));
        return Response.noContent().build();
    }

    private ServiceAccountDto toDto(UserAccount account) {
        String name = account.principalName().value()
                .substring(ManageServiceAccountsUseCase.SERVICE_ACCOUNT_PRINCIPAL_PREFIX.length());
        return new ServiceAccountDto(
                account.identifier().value(),
                name,
                account.isAdmin(),
                account.createdAt().toString()
        );
    }

    private PersonalAccessTokenDto toTokenDto(PersonalAccessToken token) {
        return new PersonalAccessTokenDto(
                token.identifier().value(),
                token.name().value(),
                token.createdAt().toString(),
                token.expiresAt().map(Object::toString).orElse(null)
        );
    }
}
