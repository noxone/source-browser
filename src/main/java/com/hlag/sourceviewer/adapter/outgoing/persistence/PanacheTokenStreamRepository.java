package com.hlag.sourceviewer.adapter.outgoing.persistence;

import com.hlag.sourceviewer.adapter.outgoing.jackson.TokenStreamSerializer;
import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.source.ExtractedToken;
import com.hlag.sourceviewer.domain.model.source.TokenStream;
import com.hlag.sourceviewer.domain.port.outgoing.TokenStreamRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PanacheTokenStreamRepository
        implements TokenStreamRepository, PanacheRepositoryBase<TokenStream, Long> {

    private final TokenStreamSerializer serializer;

    @Inject
    public PanacheTokenStreamRepository(TokenStreamSerializer serializer) {
        this.serializer = serializer;
    }

    @Override
    @Transactional
    public void storeUnpublished(FileIdentifier fileIdentifier, List<ExtractedToken> tokens, Long scanJobId) {
        byte[] data = serializer.serialize(tokens);
        persist(new TokenStream(fileIdentifier, data, scanJobId));
    }

    @Override
    @Transactional
    public void publishByScanJob(Long scanJobId) {
        update("published = true WHERE scanJobId = ?1 AND published = false", scanJobId);
    }

    @Override
    @Transactional
    public void deleteSupersededByScanJob(Long scanJobId) {
        getEntityManager().createNativeQuery("""
                DELETE FROM token_stream ts
                WHERE  ts.published = true
                  AND  (ts.scan_job_id IS NULL OR ts.scan_job_id <> :scanJobId)
                  AND  EXISTS (
                           SELECT 1 FROM token_stream ts2
                           WHERE  ts2.file_id     = ts.file_id
                             AND  ts2.scan_job_id = :scanJobId
                             AND  ts2.published   = true
                       )
                """)
                .setParameter("scanJobId", scanJobId)
                .executeUpdate();
    }

    @Override
    @Transactional
    public void deleteUnpublishedByScanJob(Long scanJobId) {
        delete("scanJobId = ?1 AND published = false", scanJobId);
    }

    @Override
    public Optional<TokenStream> findByFileId(FileIdentifier fileIdentifier) {
        return find("fileIdentifier = ?1 AND published = true", fileIdentifier)
                .firstResultOptional();
    }
}
