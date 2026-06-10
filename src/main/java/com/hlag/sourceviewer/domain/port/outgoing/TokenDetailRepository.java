package com.hlag.sourceviewer.domain.port.outgoing;

import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.source.TokenDetail;

import java.util.List;
import java.util.Optional;

public interface TokenDetailRepository {

    Optional<TokenDetail> findByFileAndPosition(FileIdentifier fileIdentifier, int line, int columnStart);

    List<TokenDetail> findByFile(FileIdentifier fileIdentifier);

    void insertUnpublished(TokenDetail tokenDetail, Long scanJobId);

    void insertAllUnpublished(List<TokenDetail> tokenDetails, Long scanJobId);

    void publishByScanJob(Long scanJobId);

    void deleteSupersededByScanJob(Long scanJobId);

    void deleteUnpublishedByScanJob(Long scanJobId);

    long countPublished();
}
