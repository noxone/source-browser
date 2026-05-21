package com.hlag.sourceviewer.adapter.incoming.rest;

import com.hlag.sourceviewer.adapter.incoming.rest.dto.CreateJavadocProviderDto;
import com.hlag.sourceviewer.adapter.incoming.rest.dto.JavadocProviderDto;
import com.hlag.sourceviewer.adapter.incoming.rest.dto.UpdateJavadocProviderDto;
import com.hlag.sourceviewer.domain.model.javadoc.JavadocProvider;
import com.hlag.sourceviewer.domain.port.incoming.ManageJavadocProvidersUseCase;
import com.hlag.sourceviewer.domain.port.incoming.ManageJavadocProvidersUseCase.CreateProviderCommand;
import com.hlag.sourceviewer.domain.port.incoming.ManageJavadocProvidersUseCase.UpdateProviderCommand;
import io.quarkus.security.Authenticated;
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

import java.util.List;
import java.util.NoSuchElementException;

@Path("/api/javadoc-providers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class JavadocProviderResource {

    private static final Logger logger = LoggerFactory.getLogger(JavadocProviderResource.class);

    private final ManageJavadocProvidersUseCase useCase;

    @Inject
    public JavadocProviderResource(ManageJavadocProvidersUseCase useCase) {
        this.useCase = useCase;
    }

    @GET
    @Authenticated
    public List<JavadocProviderDto> listProviders() {
        return useCase.listProviders().stream()
                .map(this::toDto)
                .toList();
    }

    @POST
    @RolesAllowed("admin")
    public Response createProvider(CreateJavadocProviderDto request, @Context UriInfo uriInfo) {
        logger.info("Creating Javadoc provider '{}'", request.name());
        var command = new CreateProviderCommand(
                request.name(),
                request.packagePrefix(),
                request.urlTemplate(),
                request.sortOrder()
        );
        var created = useCase.createProvider(command);
        var location = uriInfo.getAbsolutePathBuilder()
                .path(String.valueOf(created.id()))
                .build();
        return Response.created(location).entity(toDto(created)).build();
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed("admin")
    public JavadocProviderDto updateProvider(@PathParam("id") Long id, UpdateJavadocProviderDto request) {
        logger.info("Updating Javadoc provider {}", id);
        var command = new UpdateProviderCommand(
                id,
                request.name(),
                request.packagePrefix(),
                request.urlTemplate(),
                request.sortOrder()
        );
        try {
            return toDto(useCase.updateProvider(command));
        } catch (NoSuchElementException e) {
            throw new NotFoundException("Javadoc provider not found: " + id);
        }
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("admin")
    public Response deleteProvider(@PathParam("id") Long id) {
        logger.info("Deleting Javadoc provider {}", id);
        useCase.deleteProvider(id);
        return Response.noContent().build();
    }

    private JavadocProviderDto toDto(JavadocProvider provider) {
        return new JavadocProviderDto(
                provider.id(),
                provider.name(),
                provider.packagePrefix(),
                provider.urlTemplate(),
                provider.sortOrder()
        );
    }
}
