package com.hlag.sourceviewer.domain.model.source;

import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Full-text search document for a source file.
 * The {@code search_vector} column is GENERATED ALWAYS in PostgreSQL and is intentionally
 * not mapped here — Hibernate must never try to write it.
 */
@Entity
@Table(name = "document")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_id", nullable = false)
    private FileIdentifier fileIdentifier;

    @Column(name = "document_type", nullable = false)
    private String documentType;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "scan_job_id")
    private Long scanJobId;

    @Column(name = "published", nullable = false)
    private boolean published = true;

    protected Document() {}

    /** Legacy constructor — inserts as published immediately (no scan_job_id). */
    public Document(FileIdentifier fileIdentifier, String documentType, String content) {
        this.fileIdentifier = fileIdentifier;
        this.documentType = documentType;
        this.content = content;
    }

    /** Scan-time constructor — inserts as unpublished; activated in the final scan transaction. */
    public Document(FileIdentifier fileIdentifier, String documentType, String content, Long scanJobId) {
        this.fileIdentifier = fileIdentifier;
        this.documentType = documentType;
        this.content = content;
        this.scanJobId = scanJobId;
        this.published = false;
    }

    public FileIdentifier fileIdentifier() { return fileIdentifier; }
    public String documentType() { return documentType; }
    public String content() { return content; }
    public Long scanJobId() { return scanJobId; }
    public boolean published() { return published; }
}
