package com.hlag.sourceviewer.adapter.outgoing.persistence.converter;

import com.hlag.sourceviewer.domain.model.identifier.SymbolIdentifier;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class SymbolIdentifierConverter
        implements AttributeConverter<SymbolIdentifier, Long> {

    @Override
    public Long convertToDatabaseColumn(SymbolIdentifier attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public SymbolIdentifier convertToEntityAttribute(Long dbData) {
        return dbData == null ? null : new SymbolIdentifier(dbData);
    }
}
