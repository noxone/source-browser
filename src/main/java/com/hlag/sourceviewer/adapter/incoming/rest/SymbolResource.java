package com.hlag.sourceviewer.adapter.incoming.rest;

import com.hlag.sourceviewer.domain.model.query.SymbolInfo;
import com.hlag.sourceviewer.domain.model.query.SymbolReferenceInfo;
import com.hlag.sourceviewer.domain.model.identifier.SymbolIdentifier;
import com.hlag.sourceviewer.domain.port.incoming.GetSymbolDetailsUseCase;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/symbols")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class SymbolResource {

    private final GetSymbolDetailsUseCase getSymbolDetailsUseCase;

    @Inject
    public SymbolResource(GetSymbolDetailsUseCase getSymbolDetailsUseCase) {
        this.getSymbolDetailsUseCase = getSymbolDetailsUseCase;
    }

    @GET
    @Path("/{symbolId}")
    public SymbolInfo getSymbol(@PathParam("symbolId") Long symbolId) {
        return getSymbolDetailsUseCase.getSymbol(new SymbolIdentifier(symbolId))
                .orElseThrow(() -> new NotFoundException("Symbol not found: " + symbolId));
    }

    @GET
    @Path("/{symbolId}/references")
    public List<SymbolReferenceInfo> getReferences(@PathParam("symbolId") Long symbolId) {
        return getSymbolDetailsUseCase.getReferences(new SymbolIdentifier(symbolId));
    }
}
