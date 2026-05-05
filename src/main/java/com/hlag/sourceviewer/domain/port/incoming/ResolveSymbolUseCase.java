package com.hlag.sourceviewer.domain.port.incoming;

import com.hlag.sourceviewer.domain.model.identifier.QualifiedName;
import com.hlag.sourceviewer.domain.model.identifier.SimpleName;
import com.hlag.sourceviewer.domain.model.identifier.SymbolIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.*;
import com.hlag.sourceviewer.domain.model.source.Symbol;
import com.hlag.sourceviewer.domain.model.source.SymbolReference;

import java.util.List;
import java.util.Optional;

/**
 * Use case: Resolves symbols and finds their declarations and usages.
 * Used by the frontend when a developer clicks on an identifier.
 */
public interface ResolveSymbolUseCase {

    /**
     * Finds the declaration of a symbol by its qualified name.
     */
    Optional<Symbol> findDeclaration(QualifiedName qualifiedName);

    /**
     * Finds all usages of a symbol.
     */
    List<SymbolReference> findReferences(SymbolIdentifier symbolIdentifier);

    /**
     * Finds all symbols with the given simple name.
     * Multiple results are possible if the name occurs in different packages.
     */
    List<Symbol> findBySimpleName(SimpleName name);
}
