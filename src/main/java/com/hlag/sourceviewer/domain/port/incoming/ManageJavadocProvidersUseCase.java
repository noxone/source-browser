package com.hlag.sourceviewer.domain.port.incoming;

import com.hlag.sourceviewer.domain.model.javadoc.JavadocProvider;

import java.util.List;

public interface ManageJavadocProvidersUseCase {

    List<JavadocProvider> listProviders();

    JavadocProvider createProvider(CreateProviderCommand command);

    JavadocProvider updateProvider(UpdateProviderCommand command);

    void deleteProvider(Long id);

    record CreateProviderCommand(String name, String packagePrefix, String urlTemplate, int sortOrder) {}

    record UpdateProviderCommand(Long id, String name, String packagePrefix, String urlTemplate, int sortOrder) {}
}
