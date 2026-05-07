package com.hlag.sourceviewer.adapter.outgoing.persistence;

import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.QualifiedName;
import com.hlag.sourceviewer.domain.model.identifier.SimpleName;
import com.hlag.sourceviewer.domain.model.identifier.SymbolIdentifier;
import com.hlag.sourceviewer.domain.model.source.Symbol;
import com.hlag.sourceviewer.domain.port.outgoing.SymbolRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PanacheSymbolRepository
        implements SymbolRepository, PanacheRepositoryBase<Symbol, Long> {

    @Override
    public Optional<Symbol> findByIdentifier(SymbolIdentifier identifier) {
        return findByIdOptional(identifier.value());
    }

    @Override
    public Optional<Symbol> findByQualifiedName(QualifiedName qualifiedName) {
        return find("qualifiedName", qualifiedName).firstResultOptional();
    }

    @Override
    public List<Symbol> findBySimpleName(SimpleName name) {
        return list("name", name);
    }

    @Override
    public List<Symbol> findByFile(FileIdentifier fileIdentifier) {
        return list("fileIdentifier", fileIdentifier);
    }

    @Override
    @Transactional
    public SymbolIdentifier insert(Symbol symbol) {
        persist(symbol);
        return symbol.identifier();
    }

    @Override
    @Transactional
    public void deleteByFile(FileIdentifier fileIdentifier) {
        delete("fileIdentifier", fileIdentifier);
    }
}
