package com.hlag.sourceviewer.domain.model.converter;

import com.hlag.sourceviewer.domain.model.repository.ContentSha;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ContentShaConverter
        implements AttributeConverter<ContentSha, String> {

    @Override
    public String convertToDatabaseColumn(ContentSha attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public ContentSha convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new ContentSha(dbData);
    }
}
