package com.hlag.sourceviewer.adapter.outgoing.persistence.converter;

import com.hlag.sourceviewer.domain.model.identifier.LineNumber;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class LineNumberConverter
        implements AttributeConverter<LineNumber, Integer> {

    @Override
    public Integer convertToDatabaseColumn(LineNumber attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public LineNumber convertToEntityAttribute(Integer dbData) {
        return dbData == null ? null : new LineNumber(dbData);
    }
}
