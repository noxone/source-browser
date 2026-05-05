package com.hlag.sourceviewer.domain.port.outgoing;

import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.QualifiedName;
import com.hlag.sourceviewer.domain.model.identifier.SimpleName;
import com.hlag.sourceviewer.domain.model.identifier.SymbolIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.*;
import com.hlag.sourceviewer.domain.model.source.Symbol;

import java.util.List;
import java.util.Optional;

/**
 * Port for accessing persisted symbols.
 * Implemented by {@code com.hlag.sourceviewer.adapter.outgoing.persistence.JooqSymbolRepository}.
 */
public interface SymbolRepository {

    Optional<Symbol> findByIdentifier(SymbolIdentifier identifier);

    Optional<Symbol> findByQualifiedName(QualifiedName qualifiedName);

    List<Symbol> findBySimpleName(SimpleName name);

    List<Symbol> findByFile(FileIdentifier fileIdentifier);

    SymbolIdentifier insert(Symbol symbol);

    void deleteByFile(FileIdentifier fileIdentifier);
}
