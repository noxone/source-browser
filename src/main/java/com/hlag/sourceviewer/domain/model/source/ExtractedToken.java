package com.hlag.sourceviewer.domain.model.source;

/**
 * A single lexical token extracted from a source file, optionally enriched with
 * semantic information during indexing.
 *
 * <p>Field names are intentionally short to reduce JSON payload size before gzip
 * compression: {@code l}=line, {@code cs}=columnStart, {@code ce}=columnEnd,
 * {@code t}=text, {@code k}=kind, {@code g}=groupId, {@code hg}=highlightGroupId,
 * {@code d}=hasDetails.</p>
 *
 * <p>{@code groupId} is set on all tokens that belong to the same import/include
 * statement. The frontend uses this for hover-underline grouping.</p>
 *
 * <p>{@code highlightGroupId} groups tokens that reference the same symbol within
 * a file. Clicking any token with an {@code hg} value highlights all tokens sharing
 * that value. Groups are pre-computed during indexing from definition locations.</p>
 *
 * <p>{@code hasDetails} is true when a {@code token_detail} row exists for this
 * position. The frontend uses it to decide whether to show the token as clickable
 * and to fire a REST call for the rich detail panel.</p>
 */
public record ExtractedToken(
        int line,
        int columnStart,
        int columnEnd,
        String text,
        TokenKind kind,
        Integer groupId,
        Integer highlightGroupId,
        boolean hasDetails
) {
    public ExtractedToken withGroupId(Integer groupId) {
        return new ExtractedToken(line, columnStart, columnEnd, text, kind, groupId, highlightGroupId, hasDetails);
    }

    public ExtractedToken withHighlightGroupId(Integer highlightGroupId) {
        return new ExtractedToken(line, columnStart, columnEnd, text, kind, groupId, highlightGroupId, hasDetails);
    }

    public ExtractedToken withHasDetails(boolean hasDetails) {
        return new ExtractedToken(line, columnStart, columnEnd, text, kind, groupId, highlightGroupId, hasDetails);
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
