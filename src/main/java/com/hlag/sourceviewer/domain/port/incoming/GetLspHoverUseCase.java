package com.hlag.sourceviewer.domain.port.incoming;

import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.source.LspHoverResult;

import java.util.Optional;

public interface GetLspHoverUseCase {

    /**
     * Queries the LSP server for hover information and definition location
     * at the given 1-based line and column within the specified file.
     *
     * @return empty when the file is not found, the LSP is unavailable, or
     *         the server returns no information for that position
     */
    Optional<LspHoverResult> getHover(FileIdentifier fileId, int line, int column);
}
