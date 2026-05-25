package com.hlag.sourceviewer.domain.model.source;

/**
 * Scan-time DTO for LSP hover + definition data at a single token position.
 * Both {@code line} and {@code col} are 1-based (domain convention).
 * Either or both payload fields may be {@code null} when the LSP returned no data.
 */
public record TokenHoverEntry(
        int line,
        int col,
        String markdown,
        String defPath,
        Integer defLine,
        Integer defCol) {

    public boolean hasContent() {
        return markdown != null || defPath != null;
    }
}
