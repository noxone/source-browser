package com.hlag.sourceviewer.adapter.outgoing.persistence.converter;

import com.hlag.sourceviewer.domain.model.identifier.ErrorMessage;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ErrorMessageConverter
        implements AttributeConverter<ErrorMessage, String> {

    @Override
    public String convertToDatabaseColumn(ErrorMessage attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public ErrorMessage convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new ErrorMessage(dbData);
    }
}
