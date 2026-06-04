package com.hlag.sourceviewer.domain.model.source;

/**
 * A single lexical token extracted from a source file, optionally enriched with
 * semantic information when the token is a symbol declaration or reference.
 *
 * <p>Field names are intentionally short to reduce JSON payload size before gzip
 * compression: {@code l}=line, {@code cs}=columnStart, {@code ce}=columnEnd,
 * {@code t}=text, {@code k}=kind, {@code q}=qualifiedName, {@code s}=symbolId,
 * {@code h}=hoverText, {@code g}=groupId.</p>
 *
 * <p>{@code groupId} is set on all tokens that belong to the same import/include
 * statement. All tokens in a group share the same {@code qualifiedName} (the full
 * import FQN) so the frontend can treat them as a single navigable link.</p>
 */
public record ExtractedToken(
        int line,
        int columnStart,
        int columnEnd,
        String text,
        TokenKind kind,
        String qualifiedName,
        Long symbolId,
        String hoverText,
        Integer groupId
) {
    public ExtractedToken withQualifiedName(String qualifiedName) {
        return new ExtractedToken(line, columnStart, columnEnd, text, kind, qualifiedName, symbolId, hoverText, groupId);
    }

    public ExtractedToken withSymbolId(Long symbolId) {
        return new ExtractedToken(line, columnStart, columnEnd, text, kind, qualifiedName, symbolId, hoverText, groupId);
    }

    public ExtractedToken withHoverText(String hoverText) {
        return new ExtractedToken(line, columnStart, columnEnd, text, kind, qualifiedName, symbolId, hoverText, groupId);
    }

    public ExtractedToken withGroupId(Integer groupId) {
        return new ExtractedToken(line, columnStart, columnEnd, text, kind, qualifiedName, symbolId, hoverText, groupId);
    }

    public boolean is(TokenKind kind, String text) {
        return is(kind) && text.equals(text());
    }

    public boolean is(TokenKind kind) {
        return kind() == kind;
    }

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
