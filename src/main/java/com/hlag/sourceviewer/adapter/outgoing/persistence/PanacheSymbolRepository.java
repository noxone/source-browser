package com.hlag.sourceviewer.adapter.outgoing.persistence;

import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.QualifiedName;
import com.hlag.sourceviewer.domain.model.identifier.SimpleName;
import com.hlag.sourceviewer.domain.model.identifier.SymbolIdentifier;
import com.hlag.sourceviewer.domain.model.source.Symbol;
import com.hlag.sourceviewer.domain.port.outgoing.SymbolRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PanacheSymbolRepository
        implements SymbolRepository, PanacheRepositoryBase<Symbol, Long> {

    @Override
    public Optional<Symbol> findByIdentifier(SymbolIdentifier identifier) {
        return findByIdOptional(identifier.value());
    }

    @Override
    public Optional<Symbol> findByQualifiedName(QualifiedName qualifiedName) {
        return find("qualifiedName = ?1 AND published = true", qualifiedName).firstResultOptional();
    }

    @Override
    public Optional<Symbol> findByQualifiedNameForScan(QualifiedName qualifiedName, Long scanJobId) {
        Optional<Symbol> unpublished = find(
                "qualifiedName = ?1 AND published = false AND scanJobId = ?2",
                qualifiedName, scanJobId).firstResultOptional();
        if (unpublished.isPresent()) {
            return unpublished;
        }
        return find("qualifiedName = ?1 AND published = true", qualifiedName).firstResultOptional();
    }

    @Override
    public Optional<Symbol> findByFileAndPositionForScan(FileIdentifier fileId, int line, int column, Long scanJobId) {
        Optional<Symbol> unpublished = find(
                "fileIdentifier = ?1 AND lineStart = ?2 AND columnStart = ?3 AND published = false AND scanJobId = ?4",
                fileId, new com.hlag.sourceviewer.domain.model.identifier.LineNumber(line),
                new com.hlag.sourceviewer.domain.model.identifier.ColumnNumber(column), scanJobId)
                .firstResultOptional();
        if (unpublished.isPresent()) {
            return unpublished;
        }
        return find("fileIdentifier = ?1 AND lineStart = ?2 AND columnStart = ?3 AND published = true",
                fileId, new com.hlag.sourceviewer.domain.model.identifier.LineNumber(line),
                new com.hlag.sourceviewer.domain.model.identifier.ColumnNumber(column))
                .firstResultOptional();
    }

    @Override
    public List<Symbol> findBySimpleName(SimpleName name) {
        return list("name = ?1 AND published = true", name);
    }

    @Override
    public List<Symbol> findByQualifiedNamePrefix(String prefix) {
        // Native SQL required: JPQL cannot navigate into a @AttributeConverter-mapped field.
        // Escape LIKE special characters so method names containing '_' are not treated as wildcards.
        String escaped = prefix.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        @SuppressWarnings("unchecked")
        java.util.List<Symbol> result = getEntityManager()
                .createNativeQuery(
                        "SELECT * FROM symbol WHERE qualified_name LIKE :prefix ESCAPE '\\' AND published = true",
                        Symbol.class)
                .setParameter("prefix", escaped + "%")
                .getResultList();
        return result;
    }

    @Override
    public List<Symbol> findByFile(FileIdentifier fileIdentifier) {
        return list("fileIdentifier = ?1 AND published = true", fileIdentifier);
    }

    @Override
    @Transactional
    public SymbolIdentifier insert(Symbol symbol) {
        persist(symbol);
        return symbol.identifier();
    }

    @Override
    @Transactional
    public SymbolIdentifier insertUnpublished(Symbol symbol, Long scanJobId) {
        symbol.markUnpublished(scanJobId);
        persist(symbol);
        return symbol.identifier();
    }

    @Override
    @Transactional
    public void deleteByFile(FileIdentifier fileIdentifier) {
        delete("fileIdentifier", fileIdentifier);
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
                DELETE FROM symbol
                WHERE  published = true
                  AND  (scan_job_id IS NULL OR scan_job_id <> :scanJobId)
                  AND  EXISTS (
                           SELECT 1 FROM symbol s2
                           WHERE  s2.file_id     = symbol.file_id
                             AND  s2.scan_job_id = :scanJobId
                             AND  s2.published   = true
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
    public long countPublished() {
        return count("published = true");
    }
}
