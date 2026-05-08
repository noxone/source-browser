package com.hlag.sourceviewer.adapter.incoming.rest.dto;

/**
 * Request body for creating or replacing a Git credential.
 *
 * <p>The {@code secret} field carries the plaintext value (token, password, etc.) that will be
 * encrypted before storage. It is accepted inbound only and is never included in any response.</p>
 *
 * @param description optional human-readable label shown in the UI; null to leave/clear the description
 * @param secret      the plaintext secret to protect; must not be blank
 */
public record SetGitCredentialDto(
        String description,
        String secret
) {}
