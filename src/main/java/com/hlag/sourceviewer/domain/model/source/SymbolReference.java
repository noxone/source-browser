package com.hlag.sourceviewer.domain.model.source;

import com.hlag.sourceviewer.domain.model.converter.ColumnNumberConverter;
import com.hlag.sourceviewer.domain.model.converter.FileIdentifierConverter;
import com.hlag.sourceviewer.domain.model.converter.LineNumberConverter;
import com.hlag.sourceviewer.domain.model.converter.ReferenceIdentifierConverter;
import com.hlag.sourceviewer.domain.model.converter.SimpleNameConverter;
import com.hlag.sourceviewer.domain.model.converter.SymbolIdentifierConverter;
import com.hlag.sourceviewer.domain.model.identifier.ColumnNumber;
import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.LineNumber;
import com.hlag.sourceviewer.domain.model.identifier.ReferenceIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.ReferenceKind;
import com.hlag.sourceviewer.domain.model.identifier.SimpleName;
import com.hlag.sourceviewer.domain.model.identifier.SymbolIdentifier;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
    @Convert(converter = ReferenceIdentifierConverter.class)
    private ReferenceIdentifier identifier;

    @Column(name = "file_id", nullable = false)
    @Convert(converter = FileIdentifierConverter.class)
    private FileIdentifier fileIdentifier;

    @Column(name = "symbol_id")
    @Convert(converter = SymbolIdentifierConverter.class)
    private SymbolIdentifier symbolIdentifier;

    @Column(name = "unresolved_name")
    @Convert(converter = SimpleNameConverter.class)
    private SimpleName unresolvedName;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private ReferenceKind kind;

    @Column(name = "line")
    @Convert(converter = LineNumberConverter.class)
    private LineNumber line;

    @Column(name = "column_start")
    @Convert(converter = ColumnNumberConverter.class)
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
