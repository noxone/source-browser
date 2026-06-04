package com.hlag.sourceviewer.application.scan;

import com.hlag.sourceviewer.domain.model.identifier.ColumnNumber;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import com.hlag.sourceviewer.domain.model.identifier.LineNumber;
import com.hlag.sourceviewer.domain.model.identifier.QualifiedName;
import com.hlag.sourceviewer.domain.model.identifier.ReferenceKind;
import com.hlag.sourceviewer.domain.model.identifier.SimpleName;
import java.util.Optional;

/**
 * An in-flight reference discovered during indexing, before it is persisted.
 *
 * <p>{@code resolvedName} carries a FQN when the indexer could determine the target
 * name (e.g. from an LSP documentSymbol response on the target file).</p>
 *
 * <p>{@code definitionFilePath}, {@code definitionLine}, and {@code definitionColumn}
 * are populated by the LSP {@code textDocument/definition} query. When present,
 * {@link com.hlag.sourceviewer.application.scan.ExecuteScanJobService} uses them to
 * look up the target symbol in the DB and derive the FQN.</p>
 */
public record PendingReference(
            Optional<QualifiedName> resolvedName,
            Optional<SimpleName> unresolvedName,
            ReferenceKind kind,
            Optional<LineNumber> line,
            Optional<ColumnNumber> column,
            Optional<FilePath> definitionFilePath,
            Optional<LineNumber> definitionLine,
            Optional<ColumnNumber> definitionColumn) {

    /** Convenience constructor for references without a LSP definition location. */
    public PendingReference(
            Optional<QualifiedName> resolvedName,
            Optional<SimpleName> unresolvedName,
            ReferenceKind kind,
            Optional<LineNumber> line,
            Optional<ColumnNumber> column) {
        this(resolvedName, unresolvedName, kind, line, column,
                Optional.empty(), Optional.empty(), Optional.empty());
    }
}