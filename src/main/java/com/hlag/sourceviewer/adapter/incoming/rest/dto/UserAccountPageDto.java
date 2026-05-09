package com.hlag.sourceviewer.adapter.incoming.rest.dto;

import java.util.List;

/**
 * Paginated list of user accounts returned by the user-listing endpoint.
 *
 * @param items      the user accounts on the requested page
 * @param totalItems total number of accounts matching the filter, across all pages
 * @param page       the one-based page number that was returned
 * @param pageSize   the requested page size
 * @param totalPages the total number of pages
 */
public record UserAccountPageDto(
        List<UserAccountDto> items,
        long totalItems,
        int page,
        int pageSize,
        int totalPages
) {}
