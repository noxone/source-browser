package com.hlag.sourceviewer.domain.port.outgoing;

import com.hlag.sourceviewer.domain.model.javadoc.JavadocProvider;

import java.util.List;
import java.util.Optional;

public interface JavadocProviderStore {

    List<JavadocProvider> findAllOrderedBySortOrder();

    Optional<JavadocProvider> findByProviderId(Long id);

    JavadocProvider save(JavadocProvider provider);

    void delete(Long id);
}
