package com.hlag.sourceviewer.adapter.outgoing.persistence.converter;

import com.hlag.sourceviewer.domain.model.identifier.ScanJobIdentifier;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ScanJobIdentifierConverter
        implements AttributeConverter<ScanJobIdentifier, Long> {

    @Override
    public Long convertToDatabaseColumn(ScanJobIdentifier attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public ScanJobIdentifier convertToEntityAttribute(Long dbData) {
        return dbData == null ? null : new ScanJobIdentifier(dbData);
    }
}
