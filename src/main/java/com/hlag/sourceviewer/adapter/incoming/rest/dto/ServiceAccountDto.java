package com.hlag.sourceviewer.adapter.incoming.rest.dto;

/**
 * Representation of a service account in API responses.
 *
 * @param id        the account identifier
 * @param name      the human-readable service account name (principal name without the {@code svc:} prefix)
 * @param admin     whether this account has administrator privileges
 * @param createdAt ISO-8601 creation timestamp
 */
public record ServiceAccountDto(
        Long id,
        String name,
        boolean admin,
        String createdAt
) {}
