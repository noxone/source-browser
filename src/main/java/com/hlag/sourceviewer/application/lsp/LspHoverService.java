package com.hlag.sourceviewer.application.lsp;

import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.source.LspHoverResult;
import com.hlag.sourceviewer.domain.port.incoming.GetLspHoverUseCase;
import com.hlag.sourceviewer.domain.port.outgoing.TokenHoverRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;

/**
 * Serves LSP hover results from the database.
 * Hover and definition data are collected at scan time and stored in {@code token_hover};
 * no live LSP connection is required at query time.
 */
@ApplicationScoped
public class LspHoverService implements GetLspHoverUseCase {

    private final TokenHoverRepository tokenHoverRepository;

    @Inject
    public LspHoverService(TokenHoverRepository tokenHoverRepository) {
        this.tokenHoverRepository = tokenHoverRepository;
    }

    protected LspHoverService() {
        this.tokenHoverRepository = null;
    }

    @Override
    public Optional<LspHoverResult> getHover(FileIdentifier fileId, int line, int column) {
        return tokenHoverRepository
                .findByFileAndPosition(fileId, line, column)
                .map(e -> new LspHoverResult(
                        Optional.ofNullable(e.markdown()),
                        Optional.ofNullable(e.defPath()),
                        Optional.ofNullable(e.defLine()),
                        Optional.ofNullable(e.defCol())));
    }
}
