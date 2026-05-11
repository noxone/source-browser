package com.hlag.sourceviewer.adapter.incoming.rest;

import com.hlag.sourceviewer.adapter.incoming.rest.dto.CreateGitProviderGroupDto;
import com.hlag.sourceviewer.adapter.incoming.rest.dto.GitCredentialDto;
import com.hlag.sourceviewer.adapter.incoming.rest.dto.GitProviderGroupDto;
import com.hlag.sourceviewer.adapter.incoming.rest.dto.RepositoryDto;
import com.hlag.sourceviewer.adapter.incoming.rest.dto.SetGitCredentialDto;
import com.hlag.sourceviewer.adapter.incoming.rest.dto.UpdateGitProviderGroupDto;
import com.hlag.sourceviewer.adapter.incoming.rest.dto.ScanTriggerResponseDto;
import com.hlag.sourceviewer.domain.model.identifier.BranchName;
import com.hlag.sourceviewer.domain.model.identifier.CommitSha;
import com.hlag.sourceviewer.domain.model.identifier.CredentialDescription;
import com.hlag.sourceviewer.domain.model.identifier.DisplayName;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import com.hlag.sourceviewer.domain.model.identifier.GitProviderGroupIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.GitProviderType;
import com.hlag.sourceviewer.domain.model.identifier.GroupPath;
import com.hlag.sourceviewer.domain.model.identifier.SecretValue;
import com.hlag.sourceviewer.domain.model.repository.GitCredential;
import com.hlag.sourceviewer.domain.model.repository.GitProviderGroup;
import com.hlag.sourceviewer.domain.model.repository.Repository;
import com.hlag.sourceviewer.domain.port.incoming.ManageGitCredentialsUseCase;
import com.hlag.sourceviewer.domain.port.incoming.ManageGitCredentialsUseCase.SetCredentialCommand;
import com.hlag.sourceviewer.domain.port.incoming.ManageGitProviderGroupsUseCase;
import com.hlag.sourceviewer.domain.port.incoming.ManageGitProviderGroupsUseCase.CreateGitProviderGroupCommand;
import com.hlag.sourceviewer.domain.port.incoming.ManageGitProviderGroupsUseCase.UpdateGitProviderGroupCommand;
import com.hlag.sourceviewer.domain.port.incoming.SyncGroupRepositoriesUseCase;
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
    private final SyncGroupRepositoriesUseCase syncGroupRepositoriesUseCase;

    @Inject
    public GitProviderGroupResource(
            ManageGitProviderGroupsUseCase manageGitProviderGroupsUseCase,
            ManageGitCredentialsUseCase manageGitCredentialsUseCase,
            SyncGroupRepositoriesUseCase syncGroupRepositoriesUseCase) {
        this.manageGitProviderGroupsUseCase = manageGitProviderGroupsUseCase;
        this.manageGitCredentialsUseCase = manageGitCredentialsUseCase;
        this.syncGroupRepositoriesUseCase = syncGroupRepositoriesUseCase;
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
                request.forkedOmitted(),
                request.sharedOmitted(),
                request.importedOmitted()
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
                request.forkedOmitted(),
                request.sharedOmitted(),
                request.importedOmitted()
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
     * Removes the API credential for the given Git provider group.
     *
     * @param id the group identifier
     * @return 204 No Content
     */
    @DELETE
    @Path("/{id}/credential")
    public Response deleteGroupCredential(@PathParam("id") Long id) {
        logger.info("Deleting API credential for group {}", id);
        manageGitCredentialsUseCase.removeCredentialForGroup(new GitProviderGroupIdentifier(id));
        return Response.noContent().build();
    }

    /**
     * Returns the clone credential metadata for the given Git provider group.
     * This credential is used for cloning/fetching discovered repositories.
     *
     * @param id the group identifier
     * @return the clone credential metadata
     * @throws NotFoundException if the group has no clone credential configured
     */
    @GET
    @Path("/{id}/clone-credential")
    public GitCredentialDto getGroupCloneCredential(@PathParam("id") Long id) {
        return manageGitCredentialsUseCase.findCloneCredentialForGroup(new GitProviderGroupIdentifier(id))
                .map(this::toCredentialDto)
                .orElseThrow(() -> new NotFoundException("No clone credential configured for group: " + id));
    }

    /**
     * Creates or replaces the clone credential for the given Git provider group.
     *
     * @param id      the group identifier
     * @param request description and plaintext secret
     * @return the persisted credential metadata
     */
    @PUT
    @Path("/{id}/clone-credential")
    public GitCredentialDto setGroupCloneCredential(@PathParam("id") Long id, SetGitCredentialDto request) {
        logger.info("Setting clone credential for group {}", id);
        var command = new ManageGitCredentialsUseCase.SetCredentialCommand(
                Optional.ofNullable(request.description()).filter(s -> !s.isBlank()).map(CredentialDescription::new),
                new SecretValue(request.secret())
        );
        return toCredentialDto(
                manageGitCredentialsUseCase.setCloneCredentialForGroup(new GitProviderGroupIdentifier(id), command));
    }

    /**
     * Removes the clone credential for the given Git provider group.
     *
     * @param id the group identifier
     * @return 204 No Content
     */
    @DELETE
    @Path("/{id}/clone-credential")
    public Response deleteGroupCloneCredential(@PathParam("id") Long id) {
        logger.info("Deleting clone credential for group {}", id);
        manageGitCredentialsUseCase.removeCloneCredentialForGroup(new GitProviderGroupIdentifier(id));
        return Response.noContent().build();
    }

    /**
     * Returns all repositories discovered from the given Git provider group.
     *
     * @param id the group identifier
     * @return list of repositories belonging to this group
     * @throws NotFoundException if the group does not exist
     */
    @GET
    @Path("/{id}/repositories")
    public List<RepositoryDto> listGroupRepositories(@PathParam("id") Long id) {
        manageGitProviderGroupsUseCase.findGitProviderGroup(new GitProviderGroupIdentifier(id))
                .orElseThrow(() -> new NotFoundException("Git provider group not found: " + id));
        return manageGitProviderGroupsUseCase.listGroupRepositories(new GitProviderGroupIdentifier(id))
                .stream()
                .map(this::toRepositoryDto)
                .toList();
    }

    /**
     * Triggers a synchronisation of the repository list for the given Git provider group.
     * Fetches the current list from the provider API and upserts results into the local store.
     *
     * @param id the group identifier
     * @return 202 Accepted on success, 502 Bad Gateway if the provider API call fails
     * @throws NotFoundException if the group does not exist
     */
    @POST
    @Path("/{id}/scan")
    public Response triggerGroupScan(@PathParam("id") Long id) {
        logger.info("Manual scan triggered for group {}", id);
        var identifier = new GitProviderGroupIdentifier(id);
        manageGitProviderGroupsUseCase.findGitProviderGroup(identifier)
                .orElseThrow(() -> new NotFoundException("Git provider group not found: " + id));
        try {
            syncGroupRepositoriesUseCase.syncGroup(identifier);
            return Response.accepted().build();
        } catch (NoSuchElementException | IllegalStateException e) {
            logger.warn("Group sync failed for group {}: {}", id, e.getMessage());
            return Response.status(Response.Status.BAD_GATEWAY)
                    .entity("{\"error\": \"" + e.getMessage() + "\"}")
                    .build();
        } catch (Exception e) {
            logger.error("Group sync failed for group {}", id, e);
            return Response.status(Response.Status.BAD_GATEWAY)
                    .entity("{\"error\": \"Provider API error: " + e.getMessage() + "\"}")
                    .build();
        }
    }

    private GitProviderGroupDto toDto(GitProviderGroup group) {
        long repoCount = manageGitProviderGroupsUseCase.countGroupRepositories(group.identifier());
        return new GitProviderGroupDto(
                group.identifier().value(),
                group.name().value(),
                group.providerType().name(),
                group.groupPath().value(),
                group.baseUrl().map(FilePath::value).orElse(null),
                group.isArchivedOmitted(),
                group.isForkedOmitted(),
                group.isSharedOmitted(),
                group.isImportedOmitted(),
                repoCount
        );
    }

    private GitCredentialDto toCredentialDto(GitCredential credential) {
        return new GitCredentialDto(
                credential.identifier().value(),
                credential.description().map(CredentialDescription::value).orElse(null),
                credential.updatedAt()
        );
    }

    private RepositoryDto toRepositoryDto(Repository repository) {
        return new RepositoryDto(
                repository.identifier().value(),
                repository.name().value(),
                repository.remoteUrl().map(FilePath::value).orElse(null),
                repository.defaultBranch().value(),
                repository.lastScannedAt().map(Object::toString).orElse(null),
                repository.lastCommitSha().map(CommitSha::value).orElse(null)
        );
    }
}
