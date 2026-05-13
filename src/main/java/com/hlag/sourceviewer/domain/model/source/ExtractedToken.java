package com.hlag.sourceviewer.domain.model.source;

/**
 * A single lexical token extracted from a source file, optionally enriched with
 * semantic information when the token is a symbol declaration or reference.
 *
 * <p>Field names are intentionally short to reduce JSON payload size before gzip
 * compression: {@code l}=line, {@code cs}=columnStart, {@code ce}=columnEnd,
 * {@code t}=text, {@code k}=kind, {@code q}=qualifiedName, {@code s}=symbolId.</p>
 */
public record ExtractedToken(
        int line,
        int columnStart,
        int columnEnd,
        String text,
        TokenKind kind,
        String qualifiedName,
        Long symbolId
) {
    public enum TokenKind {
        KEYWORD,
        IDENTIFIER,
        STRING_LITERAL,
        CHAR_LITERAL,
        INTEGER_LITERAL,
        LONG_LITERAL,
        FLOAT_LITERAL,
        DOUBLE_LITERAL,
        LINE_COMMENT,
        BLOCK_COMMENT,
        JAVADOC_COMMENT,
        OPERATOR,
        SEPARATOR,
        WHITESPACE,
        OTHER
    }
}
