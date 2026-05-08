package com.hlag.sourceviewer.adapter.incoming.rest.dto;

/**
 * Representation of a user account in API responses.
 *
 * @param id            the account identifier
 * @param principalName the principal name from the identity provider
 * @param admin         whether this account has administrator privileges
 * @param createdAt     ISO-8601 creation timestamp
 */
public record UserAccountDto(
        Long id,
        String principalName,
        boolean admin,
        String createdAt
) {}
