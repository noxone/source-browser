package com.hlag.sourceviewer.adapter.outgoing.persistence.converter;

import com.hlag.sourceviewer.domain.model.identifier.CommitSha;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CommitShaConverter
        implements AttributeConverter<CommitSha, String> {

    @Override
    public String convertToDatabaseColumn(CommitSha attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public CommitSha convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new CommitSha(dbData);
    }
}
