package com.hlag.sourceviewer.domain.model.identifier;

/**
 * Identifies the Git hosting provider for a group or organization configuration.
 */
public enum GitProviderType {
    /** GitLab groups and subgroups. */
    GITLAB,
    /** GitHub organizations. */
    GITHUB
}
