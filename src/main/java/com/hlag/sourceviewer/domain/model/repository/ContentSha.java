package com.hlag.sourceviewer.domain.model.repository;

import com.hlag.sourceviewer.domain.model.identifier.ValueObject;

/**
 * SHA hash of a file's content.
 * Used to determine whether a file has changed since the last scan.
 */
public record ContentSha(String value) implements ValueObject<String> {
    public ContentSha {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ContentSha must not be blank");
        }
    }
}
