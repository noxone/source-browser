package com.hlag.sourceviewer.domain.model.repository;

/**
 * A repository discovered from a Git provider group via its API.
 *
 * @param name          repository display name
 * @param remoteUrl     HTTPS clone URL
 * @param defaultBranch default branch name reported by the provider
 */
public record DiscoveredRepository(String name, String remoteUrl, String defaultBranch) {}
