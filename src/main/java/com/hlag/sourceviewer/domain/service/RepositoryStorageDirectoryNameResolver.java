package com.hlag.sourceviewer.domain.service;

import com.hlag.sourceviewer.domain.model.repository.Repository;
import java.net.URI;

/** Resolves stable local directory names for repository-bound filesystem storage. */
public final class RepositoryStorageDirectoryNameResolver {

    private RepositoryStorageDirectoryNameResolver() {
    }

    /**
     * Returns the stable local directory name for a repository.
     *
     * <p>The value follows the same convention for all repository-bound storage
     * locations so caches and workspaces stay aligned with the Git checkout folder.</p>
     */
    public static String localDirectoryName(Repository repository) {
        String identifierPart = "repo-" + repository.identifier().value();
        return repository.remoteUrl()
                .map(url -> identifierPart + "-" + sanitizeRemotePath(url.value()))
                .orElse(identifierPart);
    }

    static String sanitizeRemotePath(String remoteUrl) {
        try {
            String path = URI.create(remoteUrl).getPath();
            if (path.endsWith(".git")) {
                path = path.substring(0, path.length() - 4);
            }
            return path.replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-|-$", "");
        } catch (Exception exception) {
            return remoteUrl.replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-|-$", "");
        }
    }
}

