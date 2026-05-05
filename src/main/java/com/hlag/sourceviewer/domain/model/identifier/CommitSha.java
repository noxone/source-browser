package com.hlag.sourceviewer.domain.model.identifier;

/** SHA hash of a Git commit (full or abbreviated). */
public record CommitSha(String value) implements ValueObject<String> {
    public CommitSha {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("CommitSha must not be blank");
        }
        if (!value.matches("[0-9a-fA-F]{7,40}")) {
            throw new IllegalArgumentException(
                "CommitSha has invalid format (7-40 hex characters), was: " + value);
        }
    }

    /** Returns the first 7 characters of the SHA. */
    public String shortForm() {
        return value.substring(0, Math.min(7, value.length()));
    }
}
