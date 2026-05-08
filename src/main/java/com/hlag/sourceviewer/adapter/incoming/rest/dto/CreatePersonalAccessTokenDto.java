package com.hlag.sourceviewer.adapter.incoming.rest.dto;

/**
 * Request body for creating a personal access token.
 *
 * @param name      a human-readable label, e.g. "CI pipeline"
 * @param expiresAt optional ISO-8601 expiry timestamp; omit or set to {@code null} for no expiry
 */
public record CreatePersonalAccessTokenDto(
        String name,
        String expiresAt
) {}
