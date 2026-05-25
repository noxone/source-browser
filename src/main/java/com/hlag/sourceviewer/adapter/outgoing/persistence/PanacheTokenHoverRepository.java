package com.hlag.sourceviewer.adapter.outgoing.persistence;

import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.source.TokenHover;
import com.hlag.sourceviewer.domain.model.source.TokenHoverEntry;
import com.hlag.sourceviewer.domain.port.outgoing.TokenHoverRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PanacheTokenHoverRepository
        implements TokenHoverRepository, PanacheRepositoryBase<TokenHover, Long> {

    @Override
    @Transactional
    public void storeUnpublished(FileIdentifier fileId, List<TokenHoverEntry> entries, Long scanJobId) {
        for (TokenHoverEntry e : entries) {
            persist(new TokenHover(fileId, e.line(), e.col(),
                    e.markdown(), e.defPath(), e.defLine(), e.defCol(), scanJobId));
        }
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
                DELETE FROM token_hover th
                WHERE  th.published = true
                  AND  (th.scan_job_id IS NULL OR th.scan_job_id <> :scanJobId)
                  AND  EXISTS (
                           SELECT 1 FROM token_hover th2
                           WHERE  th2.file_id     = th.file_id
                             AND  th2.scan_job_id = :scanJobId
                             AND  th2.published   = true
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
    public Optional<TokenHoverEntry> findByFileAndPosition(FileIdentifier fileId, int line, int col) {
        return find("fileIdentifier = ?1 AND line = ?2 AND col = ?3 AND published = true",
                fileId, line, col)
                .firstResultOptional()
                .map(th -> new TokenHoverEntry(th.line(), th.col(),
                        th.markdown(), th.defPath(), th.defLine(), th.defCol()));
    }
}
