package com.hlag.sourceviewer.adapter.outgoing.persistence.converter;

import com.hlag.sourceviewer.domain.model.identifier.LineNumber;

/** jOOQ-Converter zwischen {@code INTEGER} und {@link LineNumber}. */
public class LineNumberConverter extends IntValueObjectConverter<LineNumber> {
    public LineNumberConverter() {
        super(LineNumber.class, LineNumber::new);
    }
}
