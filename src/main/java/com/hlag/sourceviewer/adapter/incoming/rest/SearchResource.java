package com.hlag.sourceviewer.adapter.incoming.rest;

import com.hlag.sourceviewer.adapter.incoming.rest.dto.SearchResultDto;
import com.hlag.sourceviewer.domain.model.identifier.RepositoryIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.SimpleName;
import com.hlag.sourceviewer.domain.model.search.SearchQuery;
import com.hlag.sourceviewer.domain.model.search.SearchResult;
import com.hlag.sourceviewer.domain.port.incoming.SearchDocumentsUseCase;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/search")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class SearchResource {

    private final SearchDocumentsUseCase searchDocumentsUseCase;

    @Inject
    public SearchResource(SearchDocumentsUseCase searchDocumentsUseCase) {
        this.searchDocumentsUseCase = searchDocumentsUseCase;
    }

    @GET
    public List<SearchResultDto> search(
            @QueryParam("q") String q,
            @QueryParam("maxResults") @DefaultValue("50") int maxResults,
            @QueryParam("offset") @DefaultValue("0") int offset,
            @QueryParam("repoIds") List<Long> repoIds,
            @QueryParam("fileFilter") String fileFilter) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        List<RepositoryIdentifier> repoIdentifiers = (repoIds == null ? List.<Long>of() : repoIds)
                .stream()
                .filter(id -> id != null && id > 0)
                .map(RepositoryIdentifier::new)
                .toList();
        String normalizedFileFilter = (fileFilter == null || fileFilter.isBlank()) ? null : fileFilter.strip();
        var query = new SearchQuery(new SimpleName(q), repoIdentifiers, maxResults, offset, normalizedFileFilter);
        return searchDocumentsUseCase.search(query).stream()
                .map(this::toDto)
                .toList();
    }

    private SearchResultDto toDto(SearchResult result) {
        return new SearchResultDto(
                result.fileIdentifier().value(),
                result.filePath().value(),
                result.repositoryName().value(),
                result.snippet().value(),
                result.relevanceScore()
        );
    }
}
