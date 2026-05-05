package com.hlag.sourceviewer.adapter.outgoing.persistence.converter;

import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;

/**
 * jOOQ-Converter zwischen {@code BIGINT} und {@link FileIdentifier}.
 */
public class FileIdentifierConverter extends LongIdentifierConverter<FileIdentifier> {

    public FileIdentifierConverter() {
        super(FileIdentifier.class, FileIdentifier::new);
    }
}
