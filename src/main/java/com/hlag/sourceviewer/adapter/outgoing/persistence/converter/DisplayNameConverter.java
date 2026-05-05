package com.hlag.sourceviewer.adapter.outgoing.persistence.converter;

import com.hlag.sourceviewer.domain.model.identifier.DisplayName;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class DisplayNameConverter
        implements AttributeConverter<DisplayName, String> {

    @Override
    public String convertToDatabaseColumn(DisplayName attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public DisplayName convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new DisplayName(dbData);
    }
}
