package com.hlag.sourceviewer.domain.port.incoming;

import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;

import java.util.Optional;

public interface GetFileContentUseCase {

    Optional<String> getFileContent(FileIdentifier fileIdentifier);
}
