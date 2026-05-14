package com.hlag.sourceviewer.domain.model.search;

import com.hlag.sourceviewer.domain.model.identifier.RepositoryIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.SimpleName;

import java.util.List;

public record SearchQuery(
        SimpleName searchText,
        List<RepositoryIdentifier> repositoryIdentifiers,
        int maxResults,
        int offset
) {
    public SearchQuery {
        if (maxResults <= 0) {
            throw new IllegalArgumentException("maxResults must be > 0");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        repositoryIdentifiers = List.copyOf(repositoryIdentifiers);
    }

    public static final int DEFAULT_MAX_RESULTS = 50;

    public static SearchQuery of(SimpleName searchText) {
        return new SearchQuery(searchText, List.of(), DEFAULT_MAX_RESULTS, 0);
    }
}
