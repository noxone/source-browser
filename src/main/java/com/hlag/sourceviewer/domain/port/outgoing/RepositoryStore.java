package com.hlag.sourceviewer.domain.port.outgoing;

import com.hlag.sourceviewer.domain.model.identifier.DisplayName;
import com.hlag.sourceviewer.domain.model.identifier.RepositoryIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.*;
import com.hlag.sourceviewer.domain.model.repository.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Port for accessing persisted repository entries.
 * Named RepositoryStore (not RepositoryRepository) to avoid naming conflicts
 * with the domain concept "Repository".
 */
public interface RepositoryStore {

    Optional<Repository> findByIdentifier(RepositoryIdentifier identifier);

    Optional<Repository> findByName(DisplayName name);

    List<Repository> findAll();

    RepositoryIdentifier insert(Repository repository);

    void update(Repository repository);
}
