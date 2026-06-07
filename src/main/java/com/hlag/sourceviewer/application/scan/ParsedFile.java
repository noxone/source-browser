package com.hlag.sourceviewer.application.scan;

import com.hlag.sourceviewer.domain.model.source.ExtractedToken;
import com.hlag.sourceviewer.domain.model.source.Symbol;
import com.hlag.sourceviewer.domain.model.source.TokenDetail;
import com.hlag.sourceviewer.domain.model.source.TypeHierarchyEntry;

import java.util.List;
import java.util.Map;

public record ParsedFile(
        List<Symbol> declarations,
        List<PendingReference> references,
        List<ExtractedToken> tokens,
        List<TokenDetail> tokenDetails,
        List<TypeHierarchyEntry> hierarchyEntries,
        /** Maps "line:col" position key → pre-computed highlight group ID for that token. */
        Map<String, Integer> highlightGroups) {

    public ParsedFile withReferences(List<PendingReference> references) {
        return new ParsedFile(declarations, references, tokens, tokenDetails, hierarchyEntries, highlightGroups);
    }

    public ParsedFile withTokens(List<ExtractedToken> tokens) {
        return new ParsedFile(declarations, references, tokens, tokenDetails, hierarchyEntries, highlightGroups);
    }

    public ParsedFile withTokenDetails(List<TokenDetail> tokenDetails) {
        return new ParsedFile(declarations, references, tokens, tokenDetails, hierarchyEntries, highlightGroups);
    }

    public ParsedFile withHierarchyEntries(List<TypeHierarchyEntry> hierarchyEntries) {
        return new ParsedFile(declarations, references, tokens, tokenDetails, hierarchyEntries, highlightGroups);
    }

    public ParsedFile withHighlightGroups(Map<String, Integer> highlightGroups) {
        return new ParsedFile(declarations, references, tokens, tokenDetails, hierarchyEntries, highlightGroups);
    }

    public static ParsedFile empty() {
        return new ParsedFile(List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());
    }
}
