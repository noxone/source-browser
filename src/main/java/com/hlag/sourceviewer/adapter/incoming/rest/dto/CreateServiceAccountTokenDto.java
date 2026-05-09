package com.hlag.sourceviewer.adapter.incoming.rest.dto;

/**
 * Request body for creating a personal access token on behalf of a service account.
 *
 * @param name      a human-readable label for the token
 * @param expiresAt optional ISO-8601 expiry instant; {@code null} means the token never expires
 */
public record CreateServiceAccountTokenDto(String name, String expiresAt) {}
