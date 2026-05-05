package com.hlag.sourceviewer.adapter.outgoing.persistence.converter;

import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class FileIdentifierConverter
        implements AttributeConverter<FileIdentifier, Long> {

    @Override
    public Long convertToDatabaseColumn(FileIdentifier attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public FileIdentifier convertToEntityAttribute(Long dbData) {
        return dbData == null ? null : new FileIdentifier(dbData);
    }
}
