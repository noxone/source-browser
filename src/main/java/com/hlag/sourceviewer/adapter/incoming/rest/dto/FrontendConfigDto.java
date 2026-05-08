package com.hlag.sourceviewer.adapter.incoming.rest.dto;

/**
 * OIDC configuration values the frontend reads at runtime from {@code GET /api/config}.
 * Allows swapping the identity provider without rebuilding the frontend bundle.
 *
 * @param oidcAuthority the OIDC authority URL (issuer / realm base URL)
 * @param oidcClientId  the OIDC client identifier registered with the identity provider
 */
public record FrontendConfigDto(
        String oidcAuthority,
        String oidcClientId
) {}
