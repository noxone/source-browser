package com.hlag.sourceviewer.adapter.outgoing.persistence;

import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.QualifiedName;
import com.hlag.sourceviewer.domain.model.identifier.SimpleName;
import com.hlag.sourceviewer.domain.model.identifier.SymbolIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.*;
import com.hlag.sourceviewer.domain.model.source.Symbol;
import com.hlag.sourceviewer.domain.port.outgoing.SymbolRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * jOOQ-Implementierung des {@link SymbolRepository}.
 *
 * <p>Note: The generated jOOQ table classes (e.g. {@code Tables.SYMBOL})
 * are only available after the first {@code mvn generate-sources} run.
 * Until then, the method bodies are marked as placeholders.</p>
 */
@ApplicationScoped
public class JooqSymbolRepository implements SymbolRepository {

    private static final Logger logger = LoggerFactory.getLogger(JooqSymbolRepository.class);

    private final DSLContext dslContext;

    @Inject
    public JooqSymbolRepository(DSLContext dslContext) {
        this.dslContext = dslContext;
    }

    @Override
    public Optional<Symbol> findByIdentifier(SymbolIdentifier identifier) {
        // TODO: nach mvn generate-sources implementieren:
        // return dslContext.selectFrom(Tables.SYMBOL)
        //     .where(Tables.SYMBOL.ID.eq(identifier))
        //     .fetchOptional()
        //     .map(SymbolMapper::toDomain);
        throw new UnsupportedOperationException("Will be implemented after code generation");
    }

    @Override
    public Optional<Symbol> findByQualifiedName(QualifiedName qualifiedName) {
        throw new UnsupportedOperationException("Will be implemented after code generation");
    }

    @Override
    public List<Symbol> findBySimpleName(SimpleName name) {
        throw new UnsupportedOperationException("Will be implemented after code generation");
    }

    @Override
    public List<Symbol> findByFile(FileIdentifier fileIdentifier) {
        throw new UnsupportedOperationException("Will be implemented after code generation");
    }

    @Override
    public SymbolIdentifier insert(Symbol symbol) {
        throw new UnsupportedOperationException("Will be implemented after code generation");
    }

    @Override
    public void deleteByFile(FileIdentifier fileIdentifier) {
        throw new UnsupportedOperationException("Will be implemented after code generation");
    }
}
