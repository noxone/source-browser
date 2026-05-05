package com.hlag.sourceviewer.domain.model.identifier;

/** Relative path of a file within a repository. */
public record FilePath(String value) implements ValueObject<String> {
    public FilePath {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("FilePath must not be blank");
        }
    }

    /** Returns the file extension (without dot), or an empty string. */
    public String extension() {
        int index = value.lastIndexOf('.');
        return index == -1 ? "" : value.substring(index + 1);
    }

    /** Returns true if this is a Java source file. */
    public boolean isJavaFile() {
        return "java".equals(extension());
    }
}
