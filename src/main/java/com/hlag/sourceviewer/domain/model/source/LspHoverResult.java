package com.hlag.sourceviewer.domain.model.source;

import java.util.Optional;

/**
 * Result of an LSP hover + definition query for a source position.
 *
 * <p>All fields are optional because the LSP server may return no information
 * for a given position, and definition may not be available across all languages.</p>
 */
public record LspHoverResult(
        Optional<String> markdownContent,
        Optional<String> definitionFilePath,
        Optional<Integer> definitionLine,
        Optional<Integer> definitionColumn) {

    public static LspHoverResult empty() {
        return new LspHoverResult(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }
}
