package com.hlag.sourceviewer.domain.model.source;

import com.hlag.sourceviewer.domain.model.identifier.ColumnNumber;
import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.LineNumber;
import com.hlag.sourceviewer.domain.model.identifier.ReferenceIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.ReferenceKind;
import com.hlag.sourceviewer.domain.model.identifier.SimpleName;
import com.hlag.sourceviewer.domain.model.identifier.SymbolIdentifier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Optional;

/** Eine Verwendung eines Symbols an einer konkreten Stelle im Quellcode. */
@Entity
@Table(name = "reference")
public class SymbolReference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private ReferenceIdentifier identifier;

    @Column(name = "file_id", nullable = false)
    private FileIdentifier fileIdentifier;

    @Column(name = "symbol_id")
    private SymbolIdentifier symbolIdentifier;

    @Column(name = "unresolved_name")
    private SimpleName unresolvedName;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private ReferenceKind kind;

    @Column(name = "line")
    private LineNumber line;

    @Column(name = "column_start")
    private ColumnNumber columnStart;

    protected SymbolReference() {}

    public SymbolReference(
            ReferenceIdentifier identifier,
            FileIdentifier fileIdentifier,
            Optional<SymbolIdentifier> symbolIdentifier,
            Optional<SimpleName> unresolvedName,
            ReferenceKind kind,
            Optional<LineNumber> line,
            Optional<ColumnNumber> columnStart) {
        this.identifier = identifier;
        this.fileIdentifier = fileIdentifier;
        this.symbolIdentifier = symbolIdentifier.orElse(null);
        this.unresolvedName = unresolvedName.orElse(null);
        this.kind = kind;
        this.line = line.orElse(null);
        this.columnStart = columnStart.orElse(null);
    }

    public ReferenceIdentifier identifier() { return identifier; }
    public FileIdentifier fileIdentifier() { return fileIdentifier; }
    public Optional<SymbolIdentifier> symbolIdentifier() { return Optional.ofNullable(symbolIdentifier); }
    public Optional<SimpleName> unresolvedName() { return Optional.ofNullable(unresolvedName); }
    public ReferenceKind kind() { return kind; }
    public Optional<LineNumber> line() { return Optional.ofNullable(line); }
    public Optional<ColumnNumber> columnStart() { return Optional.ofNullable(columnStart); }
}
