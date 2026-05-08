package com.hlag.sourceviewer.adapter.incoming.rest;

import com.hlag.sourceviewer.adapter.incoming.rest.dto.CreatePersonalAccessTokenDto;
import com.hlag.sourceviewer.adapter.incoming.rest.dto.PersonalAccessTokenCreatedDto;
import com.hlag.sourceviewer.adapter.incoming.rest.dto.PersonalAccessTokenDto;
import com.hlag.sourceviewer.domain.model.identifier.PersonalAccessTokenIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.PrincipalName;
import com.hlag.sourceviewer.domain.model.identifier.TokenName;
import com.hlag.sourceviewer.domain.model.token.PersonalAccessToken;
import com.hlag.sourceviewer.domain.port.incoming.ManagePersonalAccessTokensUseCase;
import com.hlag.sourceviewer.domain.port.incoming.ManagePersonalAccessTokensUseCase.CreateTokenCommand;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
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
import java.util.Optional;

/**
 * REST resource for managing personal access tokens of the authenticated user.
 */
@Path("/api/tokens")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class PersonalAccessTokenResource {

    private static final Logger logger = LoggerFactory.getLogger(PersonalAccessTokenResource.class);

    private final ManagePersonalAccessTokensUseCase manageTokensUseCase;

    @Inject
    public PersonalAccessTokenResource(ManagePersonalAccessTokensUseCase manageTokensUseCase) {
        this.manageTokensUseCase = manageTokensUseCase;
    }

    /**
     * Returns all personal access tokens owned by the currently authenticated user.
     *
     * @param identity the injected security identity of the caller
     * @return list of token metadata records
     */
    @GET
    public List<PersonalAccessTokenDto> listTokens(@Context SecurityIdentity identity) {
        var owner = new PrincipalName(identity.getPrincipal().getName());
        return manageTokensUseCase.listTokens(owner).stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Creates a new personal access token for the currently authenticated user.
     * The raw token value is returned once in the response and cannot be retrieved again.
     *
     * @param request  the creation parameters
     * @param identity the injected security identity of the caller
     * @param uriInfo  used to build the Location header
     * @return 201 Created with the token metadata and the one-time raw token value
     */
    @POST
    public Response createToken(
            CreatePersonalAccessTokenDto request,
            @Context SecurityIdentity identity,
            @Context UriInfo uriInfo) {
        var owner = new PrincipalName(identity.getPrincipal().getName());
        logger.info("Creating personal access token '{}' for '{}'", request.name(), owner.value());

        var command = new CreateTokenCommand(
                owner,
                new TokenName(request.name()),
                Optional.ofNullable(request.expiresAt()).map(Instant::parse)
        );

        var result = manageTokensUseCase.createToken(command);
        URI location = uriInfo.getAbsolutePathBuilder()
                .path(String.valueOf(result.token().identifier().value()))
                .build();

        return Response.created(location)
                .entity(new PersonalAccessTokenCreatedDto(toDto(result.token()), result.rawToken()))
                .build();
    }

    /**
     * Revokes the personal access token with the given identifier.
     * Only the owning user can revoke their own tokens.
     *
     * @param id       the token identifier
     * @param identity the injected security identity of the caller
     * @return 204 No Content
     */
    @DELETE
    @Path("/{id}")
    public Response revokeToken(@PathParam("id") Long id, @Context SecurityIdentity identity) {
        var owner = new PrincipalName(identity.getPrincipal().getName());
        manageTokensUseCase.revokeToken(new PersonalAccessTokenIdentifier(id), owner);
        return Response.noContent().build();
    }

    private PersonalAccessTokenDto toDto(PersonalAccessToken token) {
        return new PersonalAccessTokenDto(
                token.identifier().value(),
                token.name().value(),
                token.createdAt().toString(),
                token.expiresAt().map(Object::toString).orElse(null)
        );
    }
}
