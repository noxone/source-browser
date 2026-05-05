package com.hlag.sourceviewer.domain.model.search;

import com.hlag.sourceviewer.domain.model.identifier.RepositoryIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.SimpleName;
import com.hlag.sourceviewer.domain.model.identifier.*;

import java.util.Optional;

/**
 * A search query for full-text search.
 */
public record SearchQuery(
        SimpleName searchText,
        Optional<RepositoryIdentifier> repositoryIdentifier,
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
    }

    public static final int DEFAULT_MAX_RESULTS = 50;

    public static SearchQuery of(SimpleName searchText) {
        return new SearchQuery(searchText, Optional.empty(), DEFAULT_MAX_RESULTS, 0);
    }
}
