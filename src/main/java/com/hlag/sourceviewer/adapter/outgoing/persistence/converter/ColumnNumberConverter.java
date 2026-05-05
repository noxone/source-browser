package com.hlag.sourceviewer.adapter.outgoing.persistence.converter;

import com.hlag.sourceviewer.domain.model.identifier.ColumnNumber;

/** jOOQ-Converter zwischen {@code INTEGER} und {@link ColumnNumber}. */
public class ColumnNumberConverter extends IntValueObjectConverter<ColumnNumber> {
    public ColumnNumberConverter() {
        super(ColumnNumber.class, ColumnNumber::new);
    }
}
