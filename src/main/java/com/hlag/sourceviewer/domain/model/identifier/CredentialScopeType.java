package com.hlag.sourceviewer.domain.model.identifier;

/**
 * Discriminator that identifies which kind of entity a {@code GitCredential} belongs to.
 */
public enum CredentialScopeType {

    /** The credential belongs to a single Git repository. */
    REPOSITORY,

    /** The credential belongs to a Git provider group (e.g. a GitLab group or GitHub organization). */
    GROUP
}
