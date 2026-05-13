package com.hlag.sourceviewer.application.scan;

import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.source.TokenStream;
import com.hlag.sourceviewer.domain.port.incoming.GetTokenStreamUseCase;
import com.hlag.sourceviewer.domain.port.outgoing.TokenStreamRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;

@ApplicationScoped
public class GetTokenStreamService implements GetTokenStreamUseCase {

    private final TokenStreamRepository tokenStreamRepository;

    @Inject
    public GetTokenStreamService(TokenStreamRepository tokenStreamRepository) {
        this.tokenStreamRepository = tokenStreamRepository;
    }

    @Override
    public Optional<TokenStream> getTokenStream(FileIdentifier fileIdentifier) {
        return tokenStreamRepository.findByFileId(fileIdentifier);
    }
}
