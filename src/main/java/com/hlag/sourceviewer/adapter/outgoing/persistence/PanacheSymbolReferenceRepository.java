package com.hlag.sourceviewer.adapter.outgoing.persistence;

import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.ReferenceIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.SymbolIdentifier;
import com.hlag.sourceviewer.domain.model.source.SymbolReference;
import com.hlag.sourceviewer.domain.port.outgoing.SymbolReferenceRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class PanacheSymbolReferenceRepository implements SymbolReferenceRepository {

    private final EntityManager entityManager;

    @Inject
    public PanacheSymbolReferenceRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<SymbolReference> findBySymbol(SymbolIdentifier symbolIdentifier) {
        return entityManager
                .createQuery("FROM SymbolReference r WHERE r.symbolIdentifier = :id",
                        SymbolReference.class)
                .setParameter("id", symbolIdentifier)
                .getResultList();
    }

    @Override
    public List<SymbolReference> findByFile(FileIdentifier fileIdentifier) {
        return entityManager
                .createQuery("FROM SymbolReference r WHERE r.fileIdentifier = :id",
                        SymbolReference.class)
                .setParameter("id", fileIdentifier)
                .getResultList();
    }

    @Override
    @Transactional
    public ReferenceIdentifier insert(SymbolReference symbolReference) {
        entityManager.persist(symbolReference);
        return symbolReference.identifier();
    }

    @Override
    @Transactional
    public void deleteByFile(FileIdentifier fileIdentifier) {
        entityManager
                .createQuery("DELETE FROM SymbolReference r WHERE r.fileIdentifier = :id")
                .setParameter("id", fileIdentifier)
                .executeUpdate();
    }
}
