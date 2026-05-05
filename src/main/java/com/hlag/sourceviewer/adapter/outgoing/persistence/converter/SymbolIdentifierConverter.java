package com.hlag.sourceviewer.adapter.outgoing.persistence.converter;

import com.hlag.sourceviewer.domain.model.identifier.SymbolIdentifier;

/**
 * jOOQ-Converter zwischen {@code BIGINT} und {@link SymbolIdentifier}.
 */
public class SymbolIdentifierConverter extends LongIdentifierConverter<SymbolIdentifier> {

    public SymbolIdentifierConverter() {
        super(SymbolIdentifier.class, SymbolIdentifier::new);
    }
}
