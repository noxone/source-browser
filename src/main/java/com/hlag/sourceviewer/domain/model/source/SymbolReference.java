package com.hlag.sourceviewer.domain.model.source;

import com.hlag.sourceviewer.domain.model.identifier.ColumnNumber;
import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.LineNumber;
import com.hlag.sourceviewer.domain.model.identifier.ReferenceIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.ReferenceKind;
import com.hlag.sourceviewer.domain.model.identifier.SimpleName;
import com.hlag.sourceviewer.domain.model.identifier.SymbolIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.*;

import java.util.Optional;

/**
 * Eine Verwendung eines Symbols an einer konkreten Stelle im Quellcode.
 */
public record SymbolReference(
        ReferenceIdentifier identifier,
        FileIdentifier fileIdentifier,
        Optional<SymbolIdentifier> symbolIdentifier,
        Optional<SimpleName> unresolvedName,
        ReferenceKind kind,
        Optional<LineNumber> line,
        Optional<ColumnNumber> columnStart
) {}
