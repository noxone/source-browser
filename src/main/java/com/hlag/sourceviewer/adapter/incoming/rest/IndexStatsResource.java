package com.hlag.sourceviewer.adapter.incoming.rest;

import com.hlag.sourceviewer.adapter.incoming.rest.dto.IndexedRepoStatsDto;
import com.hlag.sourceviewer.adapter.incoming.rest.dto.IndexStatsDto;
import com.hlag.sourceviewer.domain.port.incoming.GetIndexStatsUseCase;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/stats")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class IndexStatsResource {

    private final GetIndexStatsUseCase getIndexStatsUseCase;

    @Inject
    public IndexStatsResource(GetIndexStatsUseCase getIndexStatsUseCase) {
        this.getIndexStatsUseCase = getIndexStatsUseCase;
    }

    @GET
    public IndexStatsDto getStats() {
        var stats = getIndexStatsUseCase.getStats();
        return new IndexStatsDto(
                stats.repositories().stream()
                        .map(r -> new IndexedRepoStatsDto(r.id(), r.name(), r.fileCount()))
                        .toList(),
                stats.totalFiles(),
                stats.totalSymbols(),
                stats.totalReferences(),
                stats.totalTokenInfos(),
                stats.totalTypeHierarchies());
    }
}
