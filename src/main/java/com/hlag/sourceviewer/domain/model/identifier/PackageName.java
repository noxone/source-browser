package com.hlag.sourceviewer.domain.model.identifier;

/** Java package name (e.g. "com.hlag.sourceviewer.domain"). */
public record PackageName(String value) implements ValueObject<String> {
    public PackageName {
        if (value == null) {
            throw new IllegalArgumentException("PackageName must not be null");
        }
    }

    public static final PackageName ROOT = new PackageName("");
}
