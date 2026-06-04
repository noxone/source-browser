package com.hlag.sourceviewer.application.scan;

import com.hlag.sourceviewer.domain.model.source.ExtractedToken;
import com.hlag.sourceviewer.domain.model.source.Symbol;
import java.util.List;

public record ParsedFile(List<Symbol> declarations, List<PendingReference> references, List<ExtractedToken> tokens) {
    public ParsedFile withReferences(List<PendingReference> references) {
        return new ParsedFile(declarations, references, tokens);
    }

    public ParsedFile withTokens(List<ExtractedToken> tokens) {
        return new ParsedFile(declarations, references, tokens);
    }

    static ParsedFile empty() {
        return new ParsedFile(List.of(), List.of(), List.of());
    }
}

