package com.hlag.sourceviewer.domain.port.outgoing;

import com.hlag.sourceviewer.domain.model.identifier.RepositoryIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.ScanJobIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.*;
import com.hlag.sourceviewer.domain.model.source.ScanJob;

import java.util.List;
import java.util.Optional;

/**
 * Port for accessing scan jobs.
 */
public interface ScanJobRepository {

    Optional<ScanJob> findByIdentifier(ScanJobIdentifier identifier);

    Optional<ScanJob> pollNextQueued();

    List<ScanJob> findByRepository(RepositoryIdentifier repositoryIdentifier);

    ScanJobIdentifier insert(ScanJob scanJob);

    void update(ScanJob scanJob);
}
