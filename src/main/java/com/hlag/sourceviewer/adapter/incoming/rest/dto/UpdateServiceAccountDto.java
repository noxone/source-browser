package com.hlag.sourceviewer.adapter.incoming.rest.dto;

/**
 * Request body for updating a service account.
 *
 * @param admin whether the account should have administrator privileges
 */
public record UpdateServiceAccountDto(boolean admin) {}
