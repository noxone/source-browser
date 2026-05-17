package com.hlag.sourceviewer.adapter.outgoing.persistence;

import com.hlag.sourceviewer.domain.model.javadoc.JavadocProvider;
import com.hlag.sourceviewer.domain.port.outgoing.JavadocProviderStore;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PanacheJavadocProviderStore
        implements JavadocProviderStore, PanacheRepositoryBase<JavadocProvider, Long> {

    @Override
    public List<JavadocProvider> findAllOrderedBySortOrder() {
        return list("ORDER BY sortOrder ASC, id ASC");
    }

    @Override
    public Optional<JavadocProvider> findByProviderId(Long id) {
        return findByIdOptional(id);
    }

    @Override
    @Transactional
    public JavadocProvider save(JavadocProvider provider) {
        if (provider.id() == null) {
            persist(provider);
            return provider;
        }
        return getEntityManager().merge(provider);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        deleteById(id);
    }
}
