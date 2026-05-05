package com.hlag.sourceviewer.domain.port.incoming;

import com.hlag.sourceviewer.domain.model.identifier.*;
import com.hlag.sourceviewer.domain.model.search.SearchQuery;
import com.hlag.sourceviewer.domain.model.search.SearchResult;

import java.util.List;

/**
 * Use case: Full-text search across all indexed documents.
 */
public interface SearchDocumentsUseCase {

    /**
     * Performs a full-text search and returns ranked results.
     *
     * @param query the search query with text and optional filters
     * @return list of results, sorted by relevance in descending order
     */
    List<SearchResult> search(SearchQuery query);
}
