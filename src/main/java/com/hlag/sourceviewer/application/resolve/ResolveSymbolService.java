package com.hlag.sourceviewer.application.resolve;

import com.hlag.sourceviewer.domain.model.identifier.QualifiedName;
import com.hlag.sourceviewer.domain.model.identifier.SimpleName;
import com.hlag.sourceviewer.domain.model.identifier.SymbolIdentifier;
import com.hlag.sourceviewer.domain.model.source.Symbol;
import com.hlag.sourceviewer.domain.model.source.SymbolReference;
import com.hlag.sourceviewer.domain.port.incoming.ResolveSymbolUseCase;
import com.hlag.sourceviewer.domain.port.outgoing.SymbolReferenceRepository;
import com.hlag.sourceviewer.domain.port.outgoing.SymbolRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ResolveSymbolService implements ResolveSymbolUseCase {

    private final SymbolRepository symbolRepository;
    private final SymbolReferenceRepository symbolReferenceRepository;

    @Inject
    public ResolveSymbolService(
            SymbolRepository symbolRepository,
            SymbolReferenceRepository symbolReferenceRepository) {
        this.symbolRepository = symbolRepository;
        this.symbolReferenceRepository = symbolReferenceRepository;
    }

    @Override
    public Optional<Symbol> findDeclaration(QualifiedName qualifiedName) {
        return symbolRepository.findByQualifiedName(qualifiedName);
    }

    @Override
    public List<SymbolReference> findReferences(SymbolIdentifier symbolIdentifier) {
        return symbolReferenceRepository.findBySymbol(symbolIdentifier);
    }

    @Override
    public List<Symbol> findBySimpleName(SimpleName name) {
        return symbolRepository.findBySimpleName(name);
    }
}
