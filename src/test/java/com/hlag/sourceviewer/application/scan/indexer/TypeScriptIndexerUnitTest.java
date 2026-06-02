package com.hlag.sourceviewer.application.scan.indexer;

import static org.assertj.core.api.Assertions.assertThat;

import com.hlag.sourceviewer.application.scan.ParsedFile;
import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import com.hlag.sourceviewer.domain.model.identifier.SymbolKind;
import com.hlag.sourceviewer.domain.model.source.ExtractedToken.TokenKind;
import com.hlag.sourceviewer.domain.model.source.Symbol;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TypeScriptIndexerUnitTest {

    private static final FileIdentifier FILE_ID = new FileIdentifier(1L);
    private static final FilePath TS_PATH = new FilePath("src/UserService.ts");

    private TypeScriptIndexer indexer;

    @BeforeEach
    void setUp() {
        indexer = new TypeScriptIndexer();
    }

    // ── lifecycle ─────────────────────────────────────────────────────────────

    @Test
    void supported_language_is_typescript() {
        assertThat(indexer.supportedLanguage()).isEqualTo("typescript");
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
        assertThat(indexer.prepare(Path.of("/any"), null)).isNull();
    }

    @Test
    void handles_ts_files() {
        assertThat(indexer.handles(new FilePath("Service.ts"))).isTrue();
    }

    @Test
    void handles_tsx_files() {
        assertThat(indexer.handles(new FilePath("Button.tsx"))).isTrue();
    }

    @Test
    void does_not_handle_js_files() {
        assertThat(indexer.handles(new FilePath("app.js"))).isFalse();
    }

    @Test
    void does_not_handle_java_files() {
        assertThat(indexer.handles(new FilePath("Foo.java"))).isFalse();
    }

    // ── token kind mapping ────────────────────────────────────────────────────

    @Test
    void keywords_are_highlighted() {
        var result = index("class interface const let var function");
        assertContainsTokenKind(result, "class",     TokenKind.KEYWORD);
        assertContainsTokenKind(result, "interface", TokenKind.KEYWORD);
        assertContainsTokenKind(result, "const",     TokenKind.KEYWORD);
        assertContainsTokenKind(result, "let",       TokenKind.KEYWORD);
        assertContainsTokenKind(result, "var",       TokenKind.KEYWORD);
        assertContainsTokenKind(result, "function",  TokenKind.KEYWORD);
    }

    @Test
    void type_keywords_are_highlighted() {
        var result = index("string number boolean any unknown never void");
        assertContainsTokenKind(result, "string",  TokenKind.KEYWORD);
        assertContainsTokenKind(result, "number",  TokenKind.KEYWORD);
        assertContainsTokenKind(result, "boolean", TokenKind.KEYWORD);
        assertContainsTokenKind(result, "any",     TokenKind.KEYWORD);
        assertContainsTokenKind(result, "unknown", TokenKind.KEYWORD);
        assertContainsTokenKind(result, "never",   TokenKind.KEYWORD);
        assertContainsTokenKind(result, "void",    TokenKind.KEYWORD);
    }

    @Test
    void boolean_and_null_literals_are_keywords() {
        var result = index("true false null undefined");
        assertContainsTokenKind(result, "true",      TokenKind.KEYWORD);
        assertContainsTokenKind(result, "false",     TokenKind.KEYWORD);
        assertContainsTokenKind(result, "null",      TokenKind.KEYWORD);
        assertContainsTokenKind(result, "undefined", TokenKind.KEYWORD);
    }

    @Test
    void identifier_is_highlighted_correctly() {
        var result = index("myVariable");
        assertContainsTokenKind(result, "myVariable", TokenKind.IDENTIFIER);
    }

    @Test
    void double_quoted_string_is_highlighted() {
        var result = index("\"hello world\"");
        assertContainsTokenKind(result, "\"hello world\"", TokenKind.STRING_LITERAL);
    }

    @Test
    void single_quoted_string_is_highlighted() {
        var result = index("'hello'");
        assertContainsTokenKind(result, "'hello'", TokenKind.STRING_LITERAL);
    }

    @Test
    void template_literal_is_highlighted() {
        var result = index("`Hello ${name}!`");
        assertThat(result.tokens())
                .anyMatch(t -> t.kind() == TokenKind.STRING_LITERAL
                             && t.text().startsWith("`"));
    }

    @Test
    void decimal_integer_mapped_to_integer_literal() {
        var result = index("42");
        assertContainsTokenKind(result, "42", TokenKind.INTEGER_LITERAL);
    }

    @Test
    void float_mapped_to_float_literal() {
        var result = index("3.14");
        assertContainsTokenKind(result, "3.14", TokenKind.FLOAT_LITERAL);
    }

    @Test
    void hex_literal_mapped_to_integer_literal() {
        var result = index("0xFF");
        assertContainsTokenKind(result, "0xFF", TokenKind.INTEGER_LITERAL);
    }

    @Test
    void bigint_mapped_to_long_literal() {
        var result = index("42n");
        assertContainsTokenKind(result, "42n", TokenKind.LONG_LITERAL);
    }

    @Test
    void line_comment_is_highlighted() {
        var result = index("// this is a comment\nconst x = 1;");
        assertThat(result.tokens())
                .anyMatch(t -> t.kind() == TokenKind.LINE_COMMENT
                             && t.text().contains("this is a comment"));
    }

    @Test
    void block_comment_is_highlighted() {
        var result = index("/* block */");
        assertContainsTokenKind(result, "/* block */", TokenKind.BLOCK_COMMENT);
    }

    @Test
    void operators_are_highlighted() {
        var result = index("=== !== => + - * /");
        assertThat(result.tokens())
                .filteredOn(t -> t.kind() == TokenKind.OPERATOR)
                .isNotEmpty();
    }

    @Test
    void separators_are_highlighted() {
        var result = index("() {} [];");
        assertThat(result.tokens())
                .filteredOn(t -> t.kind() == TokenKind.SEPARATOR)
                .isNotEmpty();
    }

    // ── symbol extraction ─────────────────────────────────────────────────────

    @Test
    void extracts_top_level_class() {
        var result = index("class UserService { }");
        assertThat(result.declarations()).hasSize(1);
        Symbol cls = result.declarations().get(0);
        assertThat(cls.kind()).isEqualTo(SymbolKind.CLASS);
        assertThat(cls.name().value()).isEqualTo("UserService");
        assertThat(cls.qualifiedName().value()).isEqualTo("UserService.UserService");
    }

    @Test
    void extracts_class_with_extends() {
        var result = index("class AdminService extends UserService { }");
        assertThat(result.declarations())
                .anyMatch(s -> s.kind() == SymbolKind.CLASS && "AdminService".equals(s.name().value()));
    }

    @Test
    void extracts_top_level_function() {
        var result = index("function greet() { }");
        assertThat(result.declarations()).hasSize(1);
        Symbol fn = result.declarations().get(0);
        assertThat(fn.kind()).isEqualTo(SymbolKind.METHOD);
        assertThat(fn.name().value()).isEqualTo("greet");
    }

    @Test
    void extracts_method_inside_class_body() {
        String src = "class Greeter {\n  greet() { }\n}";
        var result = index(src);
        assertThat(result.declarations())
                .anyMatch(s -> s.kind() == SymbolKind.METHOD && "greet".equals(s.name().value()));
    }

    @Test
    void extracts_method_with_modifiers() {
        String src = "class Service {\n  public async fetchData() { }\n}";
        var result = index(src);
        assertThat(result.declarations())
                .anyMatch(s -> s.kind() == SymbolKind.METHOD && "fetchData".equals(s.name().value()));
    }

    @Test
    void extracts_multiple_methods() {
        String src = """
                class Calc {
                  add(a: number, b: number) { return a + b; }
                  subtract(a: number, b: number) { return a - b; }
                }
                """;
        var result = index(src);
        assertThat(result.declarations())
                .filteredOn(s -> s.kind() == SymbolKind.METHOD)
                .extracting(s -> s.name().value())
                .containsExactlyInAnyOrder("add", "subtract");
    }

    @Test
    void method_qualified_name_includes_class_name() {
        String src = "class MyService {\n  doWork() { }\n}";
        var result = index(src);
        assertThat(result.declarations())
                .filteredOn(s -> s.kind() == SymbolKind.METHOD)
                .allMatch(s -> s.qualifiedName().value().contains("MyService#doWork"));
    }

    @Test
    void function_qualified_name_uses_file_stem() {
        String src = "function calculate() {}";
        var result = index(src);
        assertThat(result.declarations())
                .anyMatch(s -> "UserService.calculate".equals(s.qualifiedName().value()));
    }

    @Test
    void returns_empty_symbol_list_for_file_with_no_declarations() {
        var result = index("const x = 42;\nconsole.log(x);");
        assertThat(result.declarations()).isEmpty();
    }

    @Test
    void does_not_extract_nested_function_as_top_level() {
        // nested function inside an if block should not be detected as top-level
        String src = "if (true) {\n  function inner() {}\n}";
        var result = index(src);
        // inner is at braceDepth 1, not 0, so it should not be extracted
        assertThat(result.declarations()).isEmpty();
    }

    @Test
    void symbol_has_correct_line_number() {
        String src = "\nclass MyClass { }";
        var result = index(src);
        assertThat(result.declarations())
                .anyMatch(s -> s.kind() == SymbolKind.CLASS
                             && s.lineStart().isPresent()
                             && s.lineStart().get().value() == 2);
    }

    @Test
    void returns_no_pending_references() {
        var result = index("class Foo { bar() {} }");
        assertThat(result.references()).isEmpty();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ParsedFile index(String source) {
        return indexer.indexFile(FILE_ID, TS_PATH, source, null);
    }

    private static void assertContainsTokenKind(ParsedFile result,
                                                 String text, TokenKind expectedKind) {
        assertThat(result.tokens())
                .as("Expected token '%s' with kind %s", text, expectedKind)
                .anyMatch(t -> text.equals(t.text()) && t.kind() == expectedKind);
    }
}
