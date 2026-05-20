package com.hlag.sourceviewer.adapter.outgoing.persistence;

import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.search.DocumentSearchMatch;
import com.hlag.sourceviewer.domain.model.source.Document;
import com.hlag.sourceviewer.domain.port.outgoing.DocumentRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class PanacheDocumentRepository
        implements DocumentRepository, PanacheRepositoryBase<Document, Long> {

    // Unicode Private Use Area sentinels — guaranteed to never appear in source code
    private static final String HL_START = "";
    private static final String HL_STOP  = "";
    private static final String HEADLINE_OPTIONS =
            "MaxWords=30,MinWords=10,MaxFragments=1,StartSel=" + HL_START + ",StopSel=" + HL_STOP;

    @Override
    @SuppressWarnings("unchecked")
    public List<DocumentSearchMatch> search(String text, List<Long> repositoryIds, int maxResults, int offset, String fileFilter) {
        FileFilterParser.ParsedFilter filter = FileFilterParser.parse(fileFilter);
        boolean needsJoin = !repositoryIds.isEmpty() || filter.isActive();

        StringBuilder sql = new StringBuilder("""
                SELECT d.file_id,
                       ts_headline('simple', d.content, websearch_to_tsquery('simple', :text),
                           :options) AS snippet,
                       ts_rank(d.search_vector, websearch_to_tsquery('simple', :text)) AS rank
                FROM   document d
                """);

        if (needsJoin) {
            sql.append("JOIN   source_file sf ON sf.id = d.file_id\n");
        }

        sql.append("""
                WHERE  d.published = true
                  AND  d.search_vector @@ websearch_to_tsquery('simple', :text)
                """);

        if (!repositoryIds.isEmpty()) {
            sql.append("  AND  sf.repository_id IN (:repoIds)\n");
        }
        if (filter.hasInclude()) {
            sql.append("  AND  sf.path ~* :includePattern\n");
        }
        if (filter.hasExclude()) {
            sql.append("  AND  NOT sf.path ~* :excludePattern\n");
        }

        sql.append("ORDER  BY rank DESC\nLIMIT  :limit OFFSET :offset");

        var q = getEntityManager().createNativeQuery(sql.toString())
                .setParameter("text", text)
                .setParameter("options", HEADLINE_OPTIONS)
                .setParameter("limit", maxResults)
                .setParameter("offset", offset);

        if (!repositoryIds.isEmpty()) {
            q.setParameter("repoIds", repositoryIds);
        }
        if (filter.hasInclude()) {
            q.setParameter("includePattern", filter.includeRegex());
        }
        if (filter.hasExclude()) {
            q.setParameter("excludePattern", filter.excludeRegex());
        }

        List<Object[]> rows = q.getResultList();
        return rows.stream()
                .map(cols -> new DocumentSearchMatch(
                        new FileIdentifier(((Number) cols[0]).longValue()),
                        (String) cols[1],
                        ((Number) cols[2]).doubleValue()))
                .toList();
    }


    @Override
    @Transactional
    public void deleteByFile(FileIdentifier fileIdentifier) {
        delete("fileIdentifier", fileIdentifier);
    }

    @Override
    @Transactional
    public void insert(Document document) {
        persist(document);
    }

    @Override
    @Transactional
    public void insertUnpublished(Document document) {
        persist(document);
    }

    @Override
    @Transactional
    public void publishByScanJob(Long scanJobId) {
        update("published = true WHERE scanJobId = ?1 AND published = false", scanJobId);
    }

    @Override
    @Transactional
    public void deleteSupersededDocuments(Long scanJobId) {
        // Delete old published documents for any file that now has a freshly published document
        // from the current scan job. The self-join identifies superseded rows without needing
        // to pass the full file-id list explicitly.
        getEntityManager().createNativeQuery("""
                DELETE FROM document d
                WHERE  d.published = true
                  AND  (d.scan_job_id IS NULL OR d.scan_job_id <> :scanJobId)
                  AND  EXISTS (
                           SELECT 1 FROM document d2
                           WHERE  d2.file_id      = d.file_id
                             AND  d2.scan_job_id  = :scanJobId
                             AND  d2.published    = true
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
