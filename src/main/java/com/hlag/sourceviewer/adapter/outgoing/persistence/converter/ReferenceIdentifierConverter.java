package com.hlag.sourceviewer.adapter.outgoing.persistence.converter;

import com.hlag.sourceviewer.domain.model.identifier.ReferenceIdentifier;

/**
 * jOOQ-Converter zwischen {@code BIGINT} und {@link ReferenceIdentifier}.
 */
public class ReferenceIdentifierConverter extends LongIdentifierConverter<ReferenceIdentifier> {

    public ReferenceIdentifierConverter() {
        super(ReferenceIdentifier.class, ReferenceIdentifier::new);
    }
}
