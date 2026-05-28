package com.hlag.sourceviewer.domain.port.outgoing;

import com.hlag.sourceviewer.domain.model.repository.Repository;
import java.nio.file.Path;

/** Port for resolving and managing persistent language-server workspace directories. */
public interface LanguageServerWorkspaceStore {

    /** Returns the workspace directory path for the given repository. */
    Path resolveWorkspacePath(Repository repository);

    /** Deletes the workspace directory for the given repository, if it exists. */
    void deleteWorkspace(Repository repository);
}

