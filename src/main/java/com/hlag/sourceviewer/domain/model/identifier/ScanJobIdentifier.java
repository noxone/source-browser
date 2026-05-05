package com.hlag.sourceviewer.domain.model.identifier;

/** Unique identifier of a scan job. */
public record ScanJobIdentifier(Long value) implements ValueObject<Long> {
    public ScanJobIdentifier {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(
                "ScanJobIdentifier must be a positive number, was: " + value);
        }
    }
}
