package com.hlag.sourceviewer.application.scan.indexer;

import static org.assertj.core.api.Assertions.assertThat;

import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import com.hlag.sourceviewer.domain.model.source.ExtractedToken;
import com.hlag.sourceviewer.domain.model.source.ExtractedToken.TokenKind;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CssIndexerUnitTest {

    private static final FileIdentifier FILE_ID = new FileIdentifier(1L);

    private CssIndexer indexer;

    @BeforeEach
    void setUp() {
        indexer = new CssIndexer();
    }

    @Test
    void supported_language_is_css() {
        assertThat(indexer.supportedLanguage()).isEqualTo("css");
    }

    @Test
    void priority_is_100() {
        assertThat(indexer.priority()).isEqualTo(100);
    }

    @Test
    void analyze_always_returns_true() {
        assertThat(indexer.analyze(Path.of("/any"), List.of())).isTrue();
    }

    @Test
    void prepare_returns_null() {
        assertThat(indexer.prepare(Path.of("/any"))).isNull();
    }

    @Test
    void handles_css_files() {
        assertThat(indexer.handles(new FilePath("styles.css"))).isTrue();
    }

    @Test
    void does_not_handle_scss_files() {
        assertThat(indexer.handles(new FilePath("styles.scss"))).isFalse();
    }

    @Test
    void does_not_handle_java_files() {
        assertThat(indexer.handles(new FilePath("Foo.java"))).isFalse();
    }

    @Test
    void block_comment_mapped_to_block_comment_kind() {
        var result = index("/* a comment */");
        assertContainsTokenKind(result, "/* a comment */", TokenKind.BLOCK_COMMENT);
    }

    @Test
    void at_rule_mapped_to_keyword_kind() {
        var result = index("@media screen { }");
        assertContainsTokenKind(result, "@media", TokenKind.KEYWORD);
    }

    @Test
    void unknown_at_rule_mapped_to_keyword_kind() {
        var result = index("@custom-rule screen { }");
        assertContainsTokenKind(result, "@custom-rule", TokenKind.KEYWORD);
    }

    @Test
    void identifier_mapped_to_identifier_kind() {
        var result = index("color: red;");
        assertContainsTokenKind(result, "color", TokenKind.IDENTIFIER);
        assertContainsTokenKind(result, "red", TokenKind.IDENTIFIER);
    }

    @Test
    void string_literal_mapped_correctly() {
        var result = index("content: \"hello\";");
        assertContainsTokenKind(result, "\"hello\"", TokenKind.STRING_LITERAL);
    }

    @Test
    void integer_with_unit_mapped_to_integer_literal() {
        var result = index("width: 10px;");
        assertContainsTokenKind(result, "10px", TokenKind.INTEGER_LITERAL);
    }

    @Test
    void float_with_unit_mapped_to_float_literal() {
        var result = index("opacity: 0.5;");
        assertContainsTokenKind(result, "0.5", TokenKind.FLOAT_LITERAL);
    }

    @Test
    void separator_mapped_correctly() {
        var result = index(".foo { color: red; }");
        assertContainsTokenKind(result, "{", TokenKind.SEPARATOR);
        assertContainsTokenKind(result, "}", TokenKind.SEPARATOR);
        assertContainsTokenKind(result, ";", TokenKind.SEPARATOR);
    }

    @Test
    void whitespace_mapped_correctly() {
        var result = index("a b");
        assertThat(nonWhitespaceTokens(result)).isNotEmpty();
        assertThat(result.tokens())
                .anyMatch(t -> t.kind() == TokenKind.WHITESPACE);
    }

    @Test
    void returns_no_symbol_declarations() {
        var result = index(".foo { color: red; }");
        assertThat(result.declarations()).isEmpty();
    }

    @Test
    void returns_no_pending_references() {
        var result = index(".foo { color: red; }");
        assertThat(result.references()).isEmpty();
    }

    @Test
    void class_selector_mapped_to_identifier_kind() {
        var result = index(".myClass { }");
        assertContainsTokenKind(result, ".myClass", TokenKind.IDENTIFIER);
    }

    @Test
    void hash_color_mapped_to_identifier_kind() {
        var result = index("color: #fff;");
        assertContainsTokenKind(result, "#fff", TokenKind.IDENTIFIER);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private com.hlag.sourceviewer.application.scan.ParsedFile index(String css) {
        return indexer.indexFile(FILE_ID, new FilePath("styles.css"), css, null);
    }

    private static void assertContainsTokenKind(
            com.hlag.sourceviewer.application.scan.ParsedFile result,
            String text, TokenKind expectedKind) {
        assertThat(result.tokens())
                .as("Expected token '%s' with kind %s", text, expectedKind)
                .anyMatch(t -> text.equals(t.text()) && t.kind() == expectedKind);
    }

    private static List<ExtractedToken> nonWhitespaceTokens(
            com.hlag.sourceviewer.application.scan.ParsedFile result) {
        return result.tokens().stream()
                .filter(t -> t.kind() != TokenKind.WHITESPACE)
                .toList();
    }
}
