package com.hlag.sourceviewer.domain.model.source;

import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Rich semantic info for a single token position, extracted during indexing and served on click. */
@Entity
@Table(name = "token_detail")
public class TokenDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_id", nullable = false)
    private FileIdentifier fileIdentifier;

    @Column(name = "line", nullable = false)
    private int line;

    @Column(name = "column_start", nullable = false)
    private int columnStart;

    /** Discriminator: TYPE_REF, TYPE_DECL, VARIABLE, METHOD_CALL, METHOD_DECL, ANNOTATION, KEYWORD */
    @Column(name = "detail_type", nullable = false, length = 50)
    private String detailType;

    @Column(name = "detail", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String detail;

    @Column(name = "scan_job_id")
    private Long scanJobId;

    @Column(name = "published", nullable = false)
    private boolean published = true;

    protected TokenDetail() {}

    public TokenDetail(FileIdentifier fileIdentifier, int line, int columnStart,
                       String detailType, String detail) {
        this.fileIdentifier = fileIdentifier;
        this.line = line;
        this.columnStart = columnStart;
        this.detailType = detailType;
        this.detail = detail;
    }

    public void markUnpublished(Long scanJobId) {
        this.scanJobId = scanJobId;
        this.published = false;
    }

    public Long id() { return id; }
    public FileIdentifier fileIdentifier() { return fileIdentifier; }
    public int line() { return line; }
    public int columnStart() { return columnStart; }
    public String detailType() { return detailType; }
    public String detail() { return detail; }
    public Long scanJobId() { return scanJobId; }
    public boolean published() { return published; }
}
