package com.hlag.sourceviewer.adapter.outgoing.persistence.converter;

import com.hlag.sourceviewer.domain.model.identifier.SimpleName;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class SimpleNameConverter
        implements AttributeConverter<SimpleName, String> {

    @Override
    public String convertToDatabaseColumn(SimpleName attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public SimpleName convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new SimpleName(dbData);
    }
}
