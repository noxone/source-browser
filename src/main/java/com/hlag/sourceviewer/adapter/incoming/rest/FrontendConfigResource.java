package com.hlag.sourceviewer.adapter.incoming.rest;

import com.hlag.sourceviewer.adapter.incoming.rest.dto.FrontendConfigDto;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.inject.Inject;

/**
 * Public endpoint that serves OIDC configuration to the Vue frontend at runtime.
 *
 * <p>Allows the identity provider to be swapped (e.g. Keycloak → Microsoft Entra ID)
 * via environment variables without rebuilding the frontend bundle. This endpoint is
 * explicitly excluded from authentication requirements via the permit-all policy in
 * {@code application.properties}.</p>
 */
@Path("/api/config")
@Produces(MediaType.APPLICATION_JSON)
public class FrontendConfigResource {

    private final String oidcAuthority;
    private final String oidcClientId;

    @Inject
    public FrontendConfigResource(
            @ConfigProperty(name = "frontend.oidc.authority") String oidcAuthority,
            @ConfigProperty(name = "frontend.oidc.client-id") String oidcClientId) {
        this.oidcAuthority = oidcAuthority;
        this.oidcClientId = oidcClientId;
    }

    /**
     * Returns the current OIDC configuration for use by the frontend SPA.
     *
     * @return the OIDC authority URL and client ID
     */
    @GET
    public FrontendConfigDto getConfig() {
        return new FrontendConfigDto(oidcAuthority, oidcClientId);
    }
}
