package com.hlag.sourceviewer.adapter.outgoing.persistence.converter;

import com.hlag.sourceviewer.domain.model.identifier.BranchName;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class BranchNameConverter
        implements AttributeConverter<BranchName, String> {

    @Override
    public String convertToDatabaseColumn(BranchName attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public BranchName convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new BranchName(dbData);
    }
}
