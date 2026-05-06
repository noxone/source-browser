package com.hlag.sourceviewer.domain.model.converter;

import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class FilePathConverter
        implements AttributeConverter<FilePath, String> {

    @Override
    public String convertToDatabaseColumn(FilePath attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public FilePath convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new FilePath(dbData);
    }
}
