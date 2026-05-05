package com.hlag.sourceviewer.adapter.outgoing.persistence.converter;

import com.hlag.sourceviewer.domain.model.identifier.ColumnNumber;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ColumnNumberConverter
        implements AttributeConverter<ColumnNumber, Integer> {

    @Override
    public Integer convertToDatabaseColumn(ColumnNumber attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public ColumnNumber convertToEntityAttribute(Integer dbData) {
        return dbData == null ? null : new ColumnNumber(dbData);
    }
}
