package com.hlag.sourceviewer.adapter.outgoing.persistence.converter;

import com.hlag.sourceviewer.domain.model.identifier.QualifiedName;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class QualifiedNameConverter
        implements AttributeConverter<QualifiedName, String> {

    @Override
    public String convertToDatabaseColumn(QualifiedName attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public QualifiedName convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new QualifiedName(dbData);
    }
}
