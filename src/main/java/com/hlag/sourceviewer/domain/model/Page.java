package com.hlag.sourceviewer.domain.model;

import java.util.List;

/**
 * A single page of query results together with pagination metadata.
 *
 * @param <T>        the type of items contained in this page
 * @param items      the items on this page, never null
 * @param totalItems the total number of items matching the original query across all pages
 * @param page       the one-based page number that was requested
 * @param pageSize   the maximum number of items per page that was requested
 */
public record Page<T>(
        List<T> items,
        long totalItems,
        int page,
        int pageSize
) {
    public Page {
        if (items == null) {
            throw new IllegalArgumentException("items must not be null");
        }
        if (page < 1) {
            throw new IllegalArgumentException("page must be >= 1, was: " + page);
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be >= 1, was: " + pageSize);
        }
        if (totalItems < 0) {
            throw new IllegalArgumentException("totalItems must not be negative, was: " + totalItems);
        }
    }

    /**
     * Returns the total number of pages, which is always at least 1.
     */
    public int totalPages() {
        return (int) Math.max(1, (totalItems + pageSize - 1) / pageSize);
    }
}
