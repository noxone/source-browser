package com.hlag.sourceviewer.adapter.incoming.rest.dto;

/**
 * Representation of a personal access token in API responses.
 * The raw token value is never included — it is only returned once at creation.
 *
 * @param id        the token identifier
 * @param name      the human-readable token name
 * @param createdAt ISO-8601 creation timestamp
 * @param expiresAt ISO-8601 expiry timestamp, or {@code null} if the token never expires
 */
public record PersonalAccessTokenDto(
        Long id,
        String name,
        String createdAt,
        String expiresAt
) {}
