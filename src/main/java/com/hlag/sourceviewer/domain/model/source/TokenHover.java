package com.hlag.sourceviewer.domain.model.source;

import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Persisted LSP hover + definition entry for a single token position.
 * Collected at scan time; served from the database on interactive hover queries.
 */
@Entity
@Table(name = "token_hover")
public class TokenHover {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_id", nullable = false)
    private FileIdentifier fileIdentifier;

    @Column(name = "line", nullable = false)
    private int line;

    @Column(name = "col", nullable = false)
    private int col;

    @Column(name = "markdown")
    private String markdown;

    @Column(name = "def_path")
    private String defPath;

    @Column(name = "def_line")
    private Integer defLine;

    @Column(name = "def_col")
    private Integer defCol;

    @Column(name = "scan_job_id")
    private Long scanJobId;

    @Column(name = "published", nullable = false)
    private boolean published = false;

    protected TokenHover() {}

    public TokenHover(FileIdentifier fileIdentifier, int line, int col,
                      String markdown, String defPath, Integer defLine, Integer defCol,
                      Long scanJobId) {
        this.fileIdentifier = fileIdentifier;
        this.line = line;
        this.col = col;
        this.markdown = markdown;
        this.defPath = defPath;
        this.defLine = defLine;
        this.defCol = defCol;
        this.scanJobId = scanJobId;
        this.published = false;
    }

    public FileIdentifier fileIdentifier() { return fileIdentifier; }
    public int line() { return line; }
    public int col() { return col; }
    public String markdown() { return markdown; }
    public String defPath() { return defPath; }
    public Integer defLine() { return defLine; }
    public Integer defCol() { return defCol; }
    public Long scanJobId() { return scanJobId; }
    public boolean published() { return published; }
}
