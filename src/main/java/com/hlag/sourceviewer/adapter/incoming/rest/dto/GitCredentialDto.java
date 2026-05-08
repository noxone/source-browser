package com.hlag.sourceviewer.adapter.incoming.rest.dto;

import java.time.Instant;

/**
 * Data transfer object representing the metadata of a Git credential.
 *
 * <p>The encrypted secret is intentionally excluded from this record. The API never
 * returns the plaintext or ciphertext value in any response.</p>
 *
 * @param id          unique numeric identifier of the credential
 * @param description optional human-readable label, may be null
 * @param updatedAt   timestamp of the last secret update
 */
public record GitCredentialDto(
        Long id,
        String description,
        Instant updatedAt
) {}
