package com.hlag.sourceviewer.domain.model.source;

import com.hlag.sourceviewer.domain.model.identifier.ColumnNumber;
import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.LineNumber;
import com.hlag.sourceviewer.domain.model.identifier.QualifiedName;
import com.hlag.sourceviewer.domain.model.identifier.SimpleName;
import com.hlag.sourceviewer.domain.model.identifier.SymbolIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.SymbolKind;
import com.hlag.sourceviewer.domain.model.identifier.*;

import java.util.List;
import java.util.Optional;

/**
 * Eine Deklaration im Quellcode (Klasse, Methode, Feld, ...).
 */
public record Symbol(
        SymbolIdentifier identifier,
        FileIdentifier fileIdentifier,
        SymbolKind kind,
        SimpleName name,
        QualifiedName qualifiedName,
        Optional<SimpleName> signature,
        Optional<LineNumber> lineStart,
        Optional<LineNumber> lineEnd,
        Optional<ColumnNumber> columnStart,
        List<String> modifiers
) {}
