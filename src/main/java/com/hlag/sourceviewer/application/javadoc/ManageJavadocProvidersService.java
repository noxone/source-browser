package com.hlag.sourceviewer.application.javadoc;

import com.hlag.sourceviewer.domain.model.javadoc.JavadocProvider;
import com.hlag.sourceviewer.domain.port.incoming.ManageJavadocProvidersUseCase;
import com.hlag.sourceviewer.domain.port.outgoing.JavadocProviderStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.NoSuchElementException;

@ApplicationScoped
public class ManageJavadocProvidersService implements ManageJavadocProvidersUseCase {

    private final JavadocProviderStore store;

    @Inject
    public ManageJavadocProvidersService(JavadocProviderStore store) {
        this.store = store;
    }

    @Override
    public List<JavadocProvider> listProviders() {
        return store.findAllOrderedBySortOrder();
    }

    @Override
    public JavadocProvider createProvider(CreateProviderCommand command) {
        var provider = new JavadocProvider(
                command.name(),
                command.packagePrefix(),
                command.urlTemplate(),
                command.sortOrder()
        );
        return store.save(provider);
    }

    @Override
    public JavadocProvider updateProvider(UpdateProviderCommand command) {
        var provider = store.findByProviderId(command.id())
                .orElseThrow(() -> new NoSuchElementException("Javadoc provider not found: " + command.id()));
        provider.setName(command.name());
        provider.setPackagePrefix(command.packagePrefix());
        provider.setUrlTemplate(command.urlTemplate());
        provider.setSortOrder(command.sortOrder());
        return store.save(provider);
    }

    @Override
    public void deleteProvider(Long id) {
        store.delete(id);
    }
}
