package com.hlag.sourceviewer.adapter.incoming.rest;

import com.hlag.sourceviewer.adapter.incoming.rest.dto.CreateGitProviderGroupDto;
import com.hlag.sourceviewer.adapter.incoming.rest.dto.GitCredentialDto;
import com.hlag.sourceviewer.adapter.incoming.rest.dto.GitProviderGroupDto;
import com.hlag.sourceviewer.adapter.incoming.rest.dto.SetGitCredentialDto;
import com.hlag.sourceviewer.adapter.incoming.rest.dto.UpdateGitProviderGroupDto;
import com.hlag.sourceviewer.adapter.incoming.rest.dto.ScanTriggerResponseDto;
import com.hlag.sourceviewer.domain.model.identifier.CredentialDescription;
import com.hlag.sourceviewer.domain.model.identifier.DisplayName;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import com.hlag.sourceviewer.domain.model.identifier.GitProviderGroupIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.GitProviderType;
import com.hlag.sourceviewer.domain.model.identifier.GroupPath;
import com.hlag.sourceviewer.domain.model.identifier.SecretValue;
import com.hlag.sourceviewer.domain.model.repository.GitCredential;
import com.hlag.sourceviewer.domain.model.repository.GitProviderGroup;
import com.hlag.sourceviewer.domain.port.incoming.ManageGitCredentialsUseCase;
import com.hlag.sourceviewer.domain.port.incoming.ManageGitCredentialsUseCase.SetCredentialCommand;
import com.hlag.sourceviewer.domain.port.incoming.ManageGitProviderGroupsUseCase;
import com.hlag.sourceviewer.domain.port.incoming.ManageGitProviderGroupsUseCase.CreateGitProviderGroupCommand;
import com.hlag.sourceviewer.domain.port.incoming.ManageGitProviderGroupsUseCase.UpdateGitProviderGroupCommand;
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
import io.quarkus.security.Authenticated;
import jakarta.annotation.security.RolesAllowed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * REST resource for managing Git provider group configurations.
 */
@RolesAllowed("admin")
@Path("/api/git-provider-groups")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GitProviderGroupResource {

    private static final Logger logger = LoggerFactory.getLogger(GitProviderGroupResource.class);

    private final ManageGitProviderGroupsUseCase manageGitProviderGroupsUseCase;
    private final ManageGitCredentialsUseCase manageGitCredentialsUseCase;

    @Inject
    public GitProviderGroupResource(
            ManageGitProviderGroupsUseCase manageGitProviderGroupsUseCase,
            ManageGitCredentialsUseCase manageGitCredentialsUseCase) {
        this.manageGitProviderGroupsUseCase = manageGitProviderGroupsUseCase;
        this.manageGitCredentialsUseCase = manageGitCredentialsUseCase;
    }

    /**
     * Returns all configured Git provider groups.
     *
     * @return list of all group configurations
     */
    @GET
    public List<GitProviderGroupDto> listGitProviderGroups() {
        return manageGitProviderGroupsUseCase.listGitProviderGroups().stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Returns the Git provider group configuration with the given identifier.
     *
     * @param id the numeric identifier
     * @return the matching group configuration
     * @throws NotFoundException if no group with the given identifier exists
     */
    @GET
    @Path("/{id}")
    public GitProviderGroupDto getGitProviderGroup(@PathParam("id") Long id) {
        return manageGitProviderGroupsUseCase.findGitProviderGroup(new GitProviderGroupIdentifier(id))
                .map(this::toDto)
                .orElseThrow(() -> new NotFoundException("Git provider group not found: " + id));
    }

    /**
     * Creates a new Git provider group configuration.
     *
     * @param request  the creation parameters
     * @param uriInfo  used to build the Location header
     * @return 201 Created with the new group in the body and a Location header
     */
    @POST
    public Response createGitProviderGroup(CreateGitProviderGroupDto request, @Context UriInfo uriInfo) {
        logger.info("Creating Git provider group '{}'", request.name());

        var command = new CreateGitProviderGroupCommand(
                new DisplayName(request.name()),
                GitProviderType.valueOf(request.providerType()),
                new GroupPath(request.groupPath()),
                Optional.ofNullable(request.baseUrl()).filter(s -> !s.isBlank()).map(FilePath::new),
                request.archivedOmitted(),
                request.forkedOmitted()
        );

        var created = manageGitProviderGroupsUseCase.createGitProviderGroup(command);
        URI location = uriInfo.getAbsolutePathBuilder()
                .path(String.valueOf(created.identifier().value()))
                .build();

        return Response.created(location).entity(toDto(created)).build();
    }

    /**
     * Updates an existing Git provider group configuration.
     *
     * @param id      the identifier of the group to update
     * @param request the updated field values
     * @return the updated group configuration
     * @throws NotFoundException if no group with the given identifier exists
     */
    @PUT
    @Path("/{id}")
    public GitProviderGroupDto updateGitProviderGroup(@PathParam("id") Long id, UpdateGitProviderGroupDto request) {
        logger.info("Updating Git provider group {}", id);

        var command = new UpdateGitProviderGroupCommand(
                new GitProviderGroupIdentifier(id),
                new DisplayName(request.name()),
                GitProviderType.valueOf(request.providerType()),
                new GroupPath(request.groupPath()),
                Optional.ofNullable(request.baseUrl()).filter(s -> !s.isBlank()).map(FilePath::new),
                request.archivedOmitted(),
                request.forkedOmitted()
        );

        try {
            return toDto(manageGitProviderGroupsUseCase.updateGitProviderGroup(command));
        } catch (NoSuchElementException e) {
            throw new NotFoundException("Git provider group not found: " + id);
        }
    }

    /**
     * Deletes the Git provider group configuration with the given identifier.
     *
     * @param id the identifier of the group to delete
     * @return 204 No Content
     */
    @DELETE
    @Path("/{id}")
    public Response deleteGitProviderGroup(@PathParam("id") Long id) {
        logger.info("Deleting Git provider group {}", id);
        manageGitProviderGroupsUseCase.deleteGitProviderGroup(new GitProviderGroupIdentifier(id));
        return Response.noContent().build();
    }

    /**
     * Returns the credential metadata for the given Git provider group.
     * The secret itself is never included in the response.
     *
     * @param id the group identifier
     * @return the credential metadata
     * @throws NotFoundException if the group has no credential configured
     */
    @GET
    @Path("/{id}/credential")
    public GitCredentialDto getGroupCredential(@PathParam("id") Long id) {
        return manageGitCredentialsUseCase.findCredentialForGroup(new GitProviderGroupIdentifier(id))
                .map(this::toCredentialDto)
                .orElseThrow(() -> new NotFoundException("No credential configured for group: " + id));
    }

    /**
     * Creates or replaces the credential for the given Git provider group.
     * The plaintext secret is accepted in the request body and is encrypted before storage.
     * The response never contains the plaintext or ciphertext.
     *
     * @param id      the group identifier
     * @param request description and plaintext secret
     * @return the persisted credential metadata
     */
    @PUT
    @Path("/{id}/credential")
    public GitCredentialDto setGroupCredential(@PathParam("id") Long id, SetGitCredentialDto request) {
        logger.info("Setting credential for group {}", id);
        var command = new SetCredentialCommand(
                Optional.ofNullable(request.description()).filter(s -> !s.isBlank()).map(CredentialDescription::new),
                new SecretValue(request.secret())
        );
        return toCredentialDto(
                manageGitCredentialsUseCase.setCredentialForGroup(new GitProviderGroupIdentifier(id), command));
    }

    /**
     * Removes the credential for the given Git provider group.
     *
     * @param id the group identifier
     * @return 204 No Content
     */
    @DELETE
    @Path("/{id}/credential")
    public Response deleteGroupCredential(@PathParam("id") Long id) {
        logger.info("Deleting credential for group {}", id);
        manageGitCredentialsUseCase.removeCredentialForGroup(new GitProviderGroupIdentifier(id));
        return Response.noContent().build();
    }

    /**
     * Enqueues a manual discovery scan for the given Git provider group.
     * Group-level discovery (importing repositories from the provider API) is not yet
     * fully implemented; this endpoint validates the group exists and returns 202 Accepted
     * as a placeholder for the future scan pipeline.
     *
     * @param id the group identifier
     * @return 202 Accepted
     * @throws NotFoundException if the group does not exist
     */
    @POST
    @Path("/{id}/scan")
    public Response triggerGroupScan(@PathParam("id") Long id) {
        logger.info("Manual scan triggered for group {}", id);
        manageGitProviderGroupsUseCase.findGitProviderGroup(new GitProviderGroupIdentifier(id))
                .orElseThrow(() -> new NotFoundException("Git provider group not found: " + id));
        return Response.accepted().build();
    }

    private GitProviderGroupDto toDto(GitProviderGroup group) {
        return new GitProviderGroupDto(
                group.identifier().value(),
                group.name().value(),
                group.providerType().name(),
                group.groupPath().value(),
                group.baseUrl().map(FilePath::value).orElse(null),
                group.isArchivedOmitted(),
                group.isForkedOmitted()
        );
    }

    private GitCredentialDto toCredentialDto(GitCredential credential) {
        return new GitCredentialDto(
                credential.identifier().value(),
                credential.description().map(CredentialDescription::value).orElse(null),
                credential.updatedAt()
        );
    }
}
