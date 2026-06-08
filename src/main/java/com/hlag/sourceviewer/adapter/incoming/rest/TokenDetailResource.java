package com.hlag.sourceviewer.adapter.incoming.rest;

import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.port.incoming.GetTokenDetailUseCase;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

@Path("/api/files")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class TokenDetailResource {

    private final GetTokenDetailUseCase getTokenDetailUseCase;

    @Inject
    public TokenDetailResource(GetTokenDetailUseCase getTokenDetailUseCase) {
        this.getTokenDetailUseCase = getTokenDetailUseCase;
    }

    @GET
    @Path("/{fileId}/tokens/{line}/{col}/details")
    public Map<String, Object> getTokenDetail(
            @PathParam("fileId") Long fileId,
            @PathParam("line") int line,
            @PathParam("col") int col) {
        return getTokenDetailUseCase.getDetail(new FileIdentifier(fileId), line, col)
                .orElseThrow(() -> new NotFoundException(
                        "No token detail at " + fileId + ":" + line + ":" + col));
    }
}
