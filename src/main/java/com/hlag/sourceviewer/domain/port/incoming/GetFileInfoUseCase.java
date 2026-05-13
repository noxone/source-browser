package com.hlag.sourceviewer.domain.port.incoming;

import com.hlag.sourceviewer.adapter.incoming.rest.dto.FileInfoDto;
import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;

import java.util.Optional;

public interface GetFileInfoUseCase {

    Optional<FileInfoDto> getFileInfo(FileIdentifier fileIdentifier);
}
