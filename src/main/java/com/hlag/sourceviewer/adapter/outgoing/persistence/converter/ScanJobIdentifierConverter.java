package com.hlag.sourceviewer.adapter.outgoing.persistence.converter;

import com.hlag.sourceviewer.domain.model.identifier.ScanJobIdentifier;

/**
 * jOOQ-Converter zwischen {@code BIGINT} und {@link ScanJobIdentifier}.
 */
public class ScanJobIdentifierConverter extends LongIdentifierConverter<ScanJobIdentifier> {

    public ScanJobIdentifierConverter() {
        super(ScanJobIdentifier.class, ScanJobIdentifier::new);
    }
}
