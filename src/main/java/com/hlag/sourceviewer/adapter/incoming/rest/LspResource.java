package com.hlag.sourceviewer.adapter.incoming.rest;

import com.hlag.sourceviewer.adapter.incoming.rest.dto.LspHoverResultDto;
import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.source.LspHoverResult;
import com.hlag.sourceviewer.domain.port.incoming.GetLspHoverUseCase;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/api/lsp")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class LspResource {

    private final GetLspHoverUseCase getLspHoverUseCase;

    @Inject
    public LspResource(GetLspHoverUseCase getLspHoverUseCase) {
        this.getLspHoverUseCase = getLspHoverUseCase;
    }

    /**
     * Returns hover and definition information for a given source position.
     *
     * @param fileId file identifier as stored in the database
     * @param line   1-based line number
     * @param column 1-based column number
     */
    @GET
    @Path("/hover")
    public LspHoverResultDto getHover(
            @QueryParam("fileId") Long fileId,
            @QueryParam("line") int line,
            @QueryParam("column") int column) {
        if (fileId == null) {
            throw new jakarta.ws.rs.BadRequestException("fileId is required");
        }
        LspHoverResult result = getLspHoverUseCase
                .getHover(new FileIdentifier(fileId), line, column)
                .orElseThrow(() -> new NotFoundException("File not found: " + fileId));
        return new LspHoverResultDto(
                result.markdownContent().orElse(null),
                result.definitionFilePath().orElse(null),
                result.definitionLine().orElse(null),
                result.definitionColumn().orElse(null));
    }
}
