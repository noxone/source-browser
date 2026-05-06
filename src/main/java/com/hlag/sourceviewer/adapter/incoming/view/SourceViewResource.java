package com.hlag.sourceviewer.adapter.incoming.view;

import com.hlag.sourceviewer.adapter.incoming.view.dto.SourceFileViewDto;
import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.SimpleName;
import com.hlag.sourceviewer.domain.port.incoming.ResolveSymbolUseCase;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/view")
public class SourceViewResource {

    private final Template fileTemplate;
    private final ResolveSymbolUseCase resolveSymbolUseCase;

    @Inject
    public SourceViewResource(
            @Location("source/file.html") Template fileTemplate,
            ResolveSymbolUseCase resolveSymbolUseCase) {
        this.fileTemplate = fileTemplate;
        this.resolveSymbolUseCase = resolveSymbolUseCase;
    }

    @GET
    @Path("/file/{fileId}")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance viewFile(@PathParam("fileId") Long fileId) {
        var symbols = resolveSymbolUseCase.findBySimpleName(new SimpleName("*"));
        var viewDto = new SourceFileViewDto(new FileIdentifier(fileId), symbols);
        return fileTemplate.data("file", viewDto);
    }
}
