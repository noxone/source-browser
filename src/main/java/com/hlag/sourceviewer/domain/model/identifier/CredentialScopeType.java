package com.hlag.sourceviewer.domain.model.identifier;

/**
 * Discriminator that identifies which kind of entity a {@code GitCredential} belongs to.
 */
public enum CredentialScopeType {

    /** The credential belongs to a single Git repository. */
    REPOSITORY,

    /** The API credential for a Git provider group — used for provider API calls (repo discovery). */
    GROUP,

    /** The clone credential for a Git provider group — used for cloning/fetching discovered repos. */
    GROUP_CLONE
}
