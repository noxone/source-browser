package com.hlag.sourceviewer.application.scan;

import com.hlag.sourceviewer.domain.model.identifier.ColumnNumber;
import com.hlag.sourceviewer.domain.model.identifier.LineNumber;
import com.hlag.sourceviewer.domain.model.identifier.QualifiedName;
import com.hlag.sourceviewer.domain.model.identifier.ReferenceKind;
import com.hlag.sourceviewer.domain.model.identifier.SimpleName;
import java.util.Optional;

public record PendingReference(
            Optional<QualifiedName> resolvedName,
            Optional<SimpleName> unresolvedName,
            ReferenceKind kind,
            Optional<LineNumber> line,
            Optional<ColumnNumber> column) {
    }