package com.hlag.sourceviewer.application.scan;

import com.hlag.sourceviewer.domain.model.source.ExtractedToken;
import com.hlag.sourceviewer.domain.model.source.Symbol;
import com.hlag.sourceviewer.domain.model.source.TokenHoverEntry;
import java.util.List;

public record ParsedFile(
        List<Symbol> declarations,
        List<PendingReference> references,
        List<ExtractedToken> tokens,
        List<TokenHoverEntry> hoverEntries) {

    static ParsedFile empty() {
        return new ParsedFile(List.of(), List.of(), List.of(), List.of());
    }
}
