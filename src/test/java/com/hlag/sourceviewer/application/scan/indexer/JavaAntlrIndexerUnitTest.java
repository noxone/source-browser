package com.hlag.sourceviewer.application.scan.indexer;

import static org.assertj.core.api.Assertions.assertThat;

import com.hlag.sourceviewer.application.scan.ParsedFile;
import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import com.hlag.sourceviewer.domain.model.identifier.SymbolKind;
import com.hlag.sourceviewer.domain.model.source.ExtractedToken.TokenKind;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JavaAntlrIndexerUnitTest {

    private static final FileIdentifier FILE_ID = new FileIdentifier(7L);
    private static final FilePath JAVA_PATH = new FilePath("src/main/java/com/example/Greeter.java");

    private JavaAntlrIndexer indexer;

    @BeforeEach
    void setUp() {
        indexer = new JavaAntlrIndexer();
    }

    @Test
    void supported_language_is_java() {
        assertThat(indexer.supportedLanguage()).isEqualTo("java");
    }

    @Test
    void priority_is_between_maven_and_generic_java_indexers() {
        assertThat(indexer.priority()).isEqualTo(50);
    }

    @Test
    void analyze_always_returns_true() {
        assertThat(indexer.analyze(Path.of("/repo"), List.of())).isTrue();
    }

    @Test
    void handles_java_files() {
        assertThat(indexer.handles(new FilePath("Foo.java"))).isTrue();
    }

    @Test
    void does_not_handle_non_java_files() {
        assertThat(indexer.handles(new FilePath("Foo.kt"))).isFalse();
    }

    @Test
    void maps_comments_to_comment_kinds() {
        ParsedFile result = index("/** docs */\n/* block */\n// line");
        assertContainsTokenKind(result, "/** docs */", TokenKind.JAVADOC_COMMENT);
        assertContainsTokenKind(result, "/* block */", TokenKind.BLOCK_COMMENT);
        assertContainsTokenKind(result, "// line", TokenKind.LINE_COMMENT);
    }

    @Test
    void maps_literals_and_keywords() {
        ParsedFile result = index("class A { String s = \"x\"; char c = 'a'; long n = 1L; }");
        assertContainsTokenKind(result, "class", TokenKind.KEYWORD);
        assertContainsTokenKind(result, "\"x\"", TokenKind.STRING_LITERAL);
        assertContainsTokenKind(result, "'a'", TokenKind.CHAR_LITERAL);
        assertContainsTokenKind(result, "1L", TokenKind.LONG_LITERAL);
    }

    @Test
    void extracts_type_member_and_constructor_symbols() {
        String source = """
                package com.example;
                public class Greeter {
                    private final String name;

                    public Greeter(String name) {
                        this.name = name;
                    }

                    public String greet() {
                        return "hi";
                    }
                }
                """;

        ParsedFile result = index(source);

        assertThat(result.declarations())
                .anyMatch(s -> s.kind() == SymbolKind.CLASS
                        && "Greeter".equals(s.name().value())
                        && "com.example.Greeter".equals(s.qualifiedName().value()));

        assertThat(result.declarations())
                .anyMatch(s -> s.kind() == SymbolKind.CONSTRUCTOR
                        && "Greeter".equals(s.name().value())
                        && "com.example.Greeter.Greeter".equals(s.qualifiedName().value()));

        assertThat(result.declarations())
                .anyMatch(s -> s.kind() == SymbolKind.METHOD
                        && "greet".equals(s.name().value())
                        && "com.example.Greeter.greet".equals(s.qualifiedName().value()));
    }

    @Test
    void extracts_interface_enum_and_record_symbols() {
        String source = """
                package com.example;
                interface Service { void run(); }
                enum Mode { FAST, SAFE }
                record Point(int x, int y) { }
                """;

        ParsedFile result = index(source);
        assertThat(result.declarations())
                .anyMatch(s -> s.kind() == SymbolKind.INTERFACE && "Service".equals(s.name().value()));
        assertThat(result.declarations())
                .anyMatch(s -> s.kind() == SymbolKind.ENUM && "Mode".equals(s.name().value()));
        assertThat(result.declarations())
                .anyMatch(s -> s.kind() == SymbolKind.RECORD && "Point".equals(s.name().value()));
    }

    @Test
    void returns_no_pending_references() {
        ParsedFile result = index("class Foo { void bar() { } }");
        assertThat(result.references()).isEmpty();
    }

    private ParsedFile index(String source) {
        return indexer.indexFile(FILE_ID, JAVA_PATH, source, null);
    }

    private static void assertContainsTokenKind(ParsedFile result, String text, TokenKind expectedKind) {
        assertThat(result.tokens())
                .as("Expected token '%s' with kind %s", text, expectedKind)
                .anyMatch(t -> text.equals(t.text()) && t.kind() == expectedKind);
    }
}
