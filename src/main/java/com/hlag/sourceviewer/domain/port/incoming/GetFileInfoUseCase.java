package com.hlag.sourceviewer.domain.port.incoming;

import com.hlag.sourceviewer.domain.model.query.FileDetails;
import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;

import java.util.Optional;

public interface GetFileInfoUseCase {

    Optional<FileDetails> getFileInfo(FileIdentifier fileIdentifier);
}
