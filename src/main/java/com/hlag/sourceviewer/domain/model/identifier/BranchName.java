package com.hlag.sourceviewer.domain.model.identifier;

/** Name of a Git branch. */
public record BranchName(String value) implements ValueObject<String> {
    public BranchName {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("BranchName must not be blank");
        }
    }

    public static final BranchName MAIN = new BranchName("main");
    public static final BranchName MASTER = new BranchName("master");
}
