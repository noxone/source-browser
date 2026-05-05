package com.hlag.sourceviewer.adapter.outgoing.persistence.converter;

import com.hlag.sourceviewer.domain.model.identifier.RepositoryIdentifier;

/**
 * jOOQ-Converter zwischen {@code BIGINT} und {@link RepositoryIdentifier}.
 */
public class RepositoryIdentifierConverter extends LongIdentifierConverter<RepositoryIdentifier> {

    public RepositoryIdentifierConverter() {
        super(RepositoryIdentifier.class, RepositoryIdentifier::new);
    }
}
