package com.hlag.sourceviewer.domain.model.converter;

import com.hlag.sourceviewer.domain.model.identifier.TokenCount;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TokenCountConverter
        implements AttributeConverter<TokenCount, Integer> {

    @Override
    public Integer convertToDatabaseColumn(TokenCount attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public TokenCount convertToEntityAttribute(Integer dbData) {
        return dbData == null ? null : new TokenCount(dbData);
    }
}
