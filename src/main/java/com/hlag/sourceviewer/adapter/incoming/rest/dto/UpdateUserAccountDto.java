package com.hlag.sourceviewer.adapter.incoming.rest.dto;

/**
 * Request body for updating a user account.
 *
 * @param admin whether the account should have administrator privileges
 */
public record UpdateUserAccountDto(boolean admin) {}
