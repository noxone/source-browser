package com.hlag.sourceviewer.adapter.outgoing.persistence.converter;

import com.hlag.sourceviewer.domain.model.identifier.RepositoryIdentifier;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class RepositoryIdentifierConverter
        implements AttributeConverter<RepositoryIdentifier, Long> {

    @Override
    public Long convertToDatabaseColumn(RepositoryIdentifier attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public RepositoryIdentifier convertToEntityAttribute(Long dbData) {
        return dbData == null ? null : new RepositoryIdentifier(dbData);
    }
}
