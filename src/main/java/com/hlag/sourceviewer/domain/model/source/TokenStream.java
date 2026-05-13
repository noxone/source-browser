package com.hlag.sourceviewer.domain.model.source;

import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Gzip-compressed JSON token stream for a source file.
 * The {@code data} column holds the byte array produced by
 * {@code TokenStreamSerializer}; callers decompress and parse it.
 */
@Entity
@Table(name = "token_stream")
public class TokenStream {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_id", nullable = false)
    private FileIdentifier fileIdentifier;

    @Column(name = "data", nullable = false)
    private byte[] data;

    @Column(name = "scan_job_id")
    private Long scanJobId;

    @Column(name = "published", nullable = false)
    private boolean published = true;

    protected TokenStream() {}

    /** Scan-time constructor — inserts as unpublished; activated in the final scan transaction. */
    public TokenStream(FileIdentifier fileIdentifier, byte[] data, Long scanJobId) {
        this.fileIdentifier = fileIdentifier;
        this.data = data;
        this.scanJobId = scanJobId;
        this.published = false;
    }

    public FileIdentifier fileIdentifier() { return fileIdentifier; }
    public byte[] data() { return data; }
    public Long scanJobId() { return scanJobId; }
    public boolean published() { return published; }
}
