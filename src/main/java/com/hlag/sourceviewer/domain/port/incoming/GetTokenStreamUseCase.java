package com.hlag.sourceviewer.domain.port.incoming;

import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.source.TokenStream;

import java.util.Optional;

/** Use case for retrieving the published token stream for a source file. */
public interface GetTokenStreamUseCase {

    /** Returns the published gzip-compressed token stream for the given file. */
    Optional<TokenStream> getTokenStream(FileIdentifier fileIdentifier);
}
