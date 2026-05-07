package com.hlag.sourceviewer.domain.model.identifier;

/**
 * The path or namespace that identifies a group within a Git provider.
 *
 * <p>For GitLab this is the group's full path (e.g. {@code my-org/my-subgroup});
 * for GitHub Organizations it is the organization login name (e.g. {@code my-org}).</p>
 */
public record GroupPath(String value) implements ValueObject<String> {
    public GroupPath {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("GroupPath must not be blank");
        }
    }
}
