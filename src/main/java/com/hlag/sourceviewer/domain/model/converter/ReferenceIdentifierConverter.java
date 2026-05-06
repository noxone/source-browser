package com.hlag.sourceviewer.domain.model.converter;

import com.hlag.sourceviewer.domain.model.identifier.ReferenceIdentifier;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ReferenceIdentifierConverter
        implements AttributeConverter<ReferenceIdentifier, Long> {

    @Override
    public Long convertToDatabaseColumn(ReferenceIdentifier attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public ReferenceIdentifier convertToEntityAttribute(Long dbData) {
        return dbData == null ? null : new ReferenceIdentifier(dbData);
    }
}
