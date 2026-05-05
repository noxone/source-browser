package com.hlag.sourceviewer.domain.model.source;

import com.hlag.sourceviewer.domain.model.identifier.ColumnNumber;
import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.LineNumber;
import com.hlag.sourceviewer.domain.model.identifier.QualifiedName;
import com.hlag.sourceviewer.domain.model.identifier.SimpleName;
import com.hlag.sourceviewer.domain.model.identifier.SymbolIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.SymbolKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Eine Deklaration im Quellcode (Klasse, Methode, Feld, ...). */
@Entity
@Table(name = "symbol")
public class Symbol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private SymbolIdentifier identifier;

    @Column(name = "file_id", nullable = false)
    private FileIdentifier fileIdentifier;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private SymbolKind kind;

    @Column(name = "name", nullable = false)
    private SimpleName name;

    @Column(name = "qualified_name", nullable = false)
    private QualifiedName qualifiedName;

    @Column(name = "signature")
    private SimpleName signature;

    @Column(name = "line_start")
    private LineNumber lineStart;

    @Column(name = "line_end")
    private LineNumber lineEnd;

    @Column(name = "column_start")
    private ColumnNumber columnStart;

    @Column(name = "modifiers", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] modifiers;

    @Column(name = "extras", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String extras;

    protected Symbol() {}

    public Symbol(
            SymbolIdentifier identifier,
            FileIdentifier fileIdentifier,
            SymbolKind kind,
            SimpleName name,
            QualifiedName qualifiedName,
            Optional<SimpleName> signature,
            Optional<LineNumber> lineStart,
            Optional<LineNumber> lineEnd,
            Optional<ColumnNumber> columnStart,
            List<String> modifiers) {
        this.identifier = identifier;
        this.fileIdentifier = fileIdentifier;
        this.kind = kind;
        this.name = name;
        this.qualifiedName = qualifiedName;
        this.signature = signature.orElse(null);
        this.lineStart = lineStart.orElse(null);
        this.lineEnd = lineEnd.orElse(null);
        this.columnStart = columnStart.orElse(null);
        this.modifiers = modifiers == null ? new String[0] : modifiers.toArray(String[]::new);
    }

    public SymbolIdentifier identifier() { return identifier; }
    public FileIdentifier fileIdentifier() { return fileIdentifier; }
    public SymbolKind kind() { return kind; }
    public SimpleName name() { return name; }
    public QualifiedName qualifiedName() { return qualifiedName; }
    public Optional<SimpleName> signature() { return Optional.ofNullable(signature); }
    public Optional<LineNumber> lineStart() { return Optional.ofNullable(lineStart); }
    public Optional<LineNumber> lineEnd() { return Optional.ofNullable(lineEnd); }
    public Optional<ColumnNumber> columnStart() { return Optional.ofNullable(columnStart); }
    public List<String> modifiers() {
        return modifiers == null ? List.of() : Arrays.asList(modifiers);
    }
}
