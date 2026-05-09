package com.hlag.sourceviewer.adapter.incoming.rest.dto;

/**
 * Request body for creating a new service account.
 *
 * @param name  the chosen name (alphanumeric, hyphens, underscores; max 64 chars)
 * @param admin whether the account should have administrator privileges
 */
public record CreateServiceAccountDto(String name, boolean admin) {}
