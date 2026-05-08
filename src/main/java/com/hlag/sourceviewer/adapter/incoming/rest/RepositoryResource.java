package com.hlag.sourceviewer.adapter.incoming.rest;

import com.hlag.sourceviewer.adapter.incoming.rest.dto.CreateRepositoryDto;
import com.hlag.sourceviewer.adapter.incoming.rest.dto.RepositoryDto;
import com.hlag.sourceviewer.adapter.incoming.rest.dto.UpdateRepositoryDto;
import com.hlag.sourceviewer.domain.model.identifier.BranchName;
import com.hlag.sourceviewer.domain.model.identifier.DisplayName;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import com.hlag.sourceviewer.domain.model.identifier.RepositoryIdentifier;
import com.hlag.sourceviewer.domain.model.repository.Repository;
import com.hlag.sourceviewer.domain.port.incoming.ManageRepositoriesUseCase;
import com.hlag.sourceviewer.domain.port.incoming.ManageRepositoriesUseCase.CreateRepositoryCommand;
import com.hlag.sourceviewer.domain.port.incoming.ManageRepositoriesUseCase.UpdateRepositoryCommand;
import io.quarkus.security.Authenticated;
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
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Path("/api/repositories")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class RepositoryResource {

    private static final Logger logger = LoggerFactory.getLogger(RepositoryResource.class);

    private final ManageRepositoriesUseCase manageRepositoriesUseCase;

    @Inject
    public RepositoryResource(ManageRepositoriesUseCase manageRepositoriesUseCase) {
        this.manageRepositoriesUseCase = manageRepositoriesUseCase;
    }

    @GET
    public List<RepositoryDto> listRepositories() {
        return manageRepositoriesUseCase.listRepositories().stream()
                .map(this::toDto)
                .toList();
    }

    @GET
    @Path("/{id}")
    public RepositoryDto getRepository(@PathParam("id") Long id) {
        return manageRepositoriesUseCase.findRepository(new RepositoryIdentifier(id))
                .map(this::toDto)
                .orElseThrow(() -> new NotFoundException("Repository not found: " + id));
    }

    @POST
    public Response createRepository(CreateRepositoryDto request, @Context UriInfo uriInfo) {
        logger.info("Creating repository '{}'", request.name());

        var command = new CreateRepositoryCommand(
                new DisplayName(request.name()),
                Optional.ofNullable(request.remoteUrl()).filter(s -> !s.isBlank()).map(FilePath::new),
                new BranchName(request.defaultBranch() != null ? request.defaultBranch() : "main")
        );

        var created = manageRepositoriesUseCase.createRepository(command);
        URI location = uriInfo.getAbsolutePathBuilder()
                .path(String.valueOf(created.identifier().value()))
                .build();

        return Response.created(location).entity(toDto(created)).build();
    }

    @PUT
    @Path("/{id}")
    public RepositoryDto updateRepository(@PathParam("id") Long id, UpdateRepositoryDto request) {
        logger.info("Updating repository {}", id);

        var command = new UpdateRepositoryCommand(
                new RepositoryIdentifier(id),
                new DisplayName(request.name()),
                Optional.ofNullable(request.remoteUrl()).filter(s -> !s.isBlank()).map(FilePath::new),
                new BranchName(request.defaultBranch() != null ? request.defaultBranch() : "main")
        );

        try {
            return toDto(manageRepositoriesUseCase.updateRepository(command));
        } catch (NoSuchElementException e) {
            throw new NotFoundException("Repository not found: " + id);
        }
    }

    @DELETE
    @Path("/{id}")
    public Response deleteRepository(@PathParam("id") Long id) {
        logger.info("Deleting repository {}", id);
        manageRepositoriesUseCase.deleteRepository(new RepositoryIdentifier(id));
        return Response.noContent().build();
    }

    private RepositoryDto toDto(Repository repository) {
        return new RepositoryDto(
                repository.identifier().value(),
                repository.name().value(),
                repository.remoteUrl().map(FilePath::value).orElse(null),
                repository.defaultBranch().value(),
                repository.lastScannedAt().map(Object::toString).orElse(null),
                repository.lastCommitSha().map(sha -> sha.value()).orElse(null)
        );
    }
}
