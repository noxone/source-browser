package com.hlag.sourceviewer.domain.port.incoming;

import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;

import java.util.Map;
import java.util.Optional;

public interface GetTokenDetailUseCase {
    Optional<Map<String, Object>> getDetail(FileIdentifier fileId, int line, int col);
}
