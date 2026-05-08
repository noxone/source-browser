package com.hlag.sourceviewer.adapter.incoming.rest.dto;

/**
 * Response returned exactly once when a personal access token is created.
 * The {@code rawToken} field contains the only opportunity to read the token value.
 *
 * @param token    the token metadata (id, name, timestamps)
 * @param rawToken the full token string including the {@code svt_} prefix; store this securely
 */
public record PersonalAccessTokenCreatedDto(
        PersonalAccessTokenDto token,
        String rawToken
) {}
