package com.hlag.sourceviewer.application.scan.indexer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hlag.sourceviewer.application.scan.ParsedFile;
import com.hlag.sourceviewer.application.scan.lsp.LanguageServerSession;
import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import com.hlag.sourceviewer.domain.model.identifier.SymbolKind;
import com.hlag.sourceviewer.domain.model.source.ExtractedToken;
import com.hlag.sourceviewer.domain.model.source.ExtractedToken.TokenKind;
import com.hlag.sourceviewer.infrastructure.lsp.jdtls.JdtlsNotifyingLanguageClient;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

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
    void priority_is_100() {
        assertThat(indexer.priority()).isEqualTo(100);
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

    // ── field extraction ──────────────────────────────────────────────────────

    @Test
    void extracts_simple_field_with_terminating_semicolon() {
        String source = """
                package com.example;
                public class Holder {
                    String value;
                }
                """;
        ParsedFile result = index(source);
        assertThat(result.declarations())
                .anyMatch(s -> s.kind() == SymbolKind.FIELD && "value".equals(s.name().value()));
    }

    @Test
    void extracts_field_with_initializer() {
        String source = """
                package com.example;
                public class Counter {
                    private String name = "default";
                }
                """;
        ParsedFile result = index(source);
        assertThat(result.declarations())
                .anyMatch(s -> s.kind() == SymbolKind.FIELD && "name".equals(s.name().value()));
    }

    @Test
    void field_qualified_name_includes_class_and_package() {
        String source = """
                package com.example;
                public class Entity {
                    String id;
                }
                """;
        ParsedFile result = index(source);
        assertThat(result.declarations())
                .filteredOn(s -> s.kind() == SymbolKind.FIELD)
                .allMatch(s -> s.qualifiedName().value().contains("Entity.id"));
    }

    // ── parameter extraction ──────────────────────────────────────────────────

    @Test
    void extracts_parameters_from_method() {
        String source = """
                package com.example;
                public class Greeter {
                    public String greet(String name, int times) {
                        return name;
                    }
                }
                """;
        ParsedFile result = index(source);
        assertThat(result.declarations())
                .anyMatch(s -> s.kind() == SymbolKind.PARAMETER && "name".equals(s.name().value()));
    }

    @Test
    void extracts_constructor_parameters() {
        String source = """
                package com.example;
                public class Person {
                    public Person(String firstName, String lastName) {}
                }
                """;
        ParsedFile result = index(source);
        assertThat(result.declarations())
                .anyMatch(s -> s.kind() == SymbolKind.PARAMETER && "firstName".equals(s.name().value()));
        assertThat(result.declarations())
                .anyMatch(s -> s.kind() == SymbolKind.PARAMETER && "lastName".equals(s.name().value()));
    }

    @Test
    void parameter_qualified_name_includes_method() {
        String source = """
                package com.example;
                public class Service {
                    public void process(String input) {}
                }
                """;
        ParsedFile result = index(source);
        assertThat(result.declarations())
                .filteredOn(s -> s.kind() == SymbolKind.PARAMETER)
                .allMatch(s -> s.qualifiedName().value().contains("process.input"));
    }

    // ── import group assignment ───────────────────────────────────────────────

    @Nested
    class AssignImportGroupsTest {

        @Test
        void simple_import_assigns_group_to_all_identifier_and_dot_tokens() {
            ParsedFile result = index("import com.example.Foo;");
            var grouped = result.tokens().stream()
                    .filter(t -> t.groupId() != null)
                    .toList();
            assertThat(grouped).isNotEmpty();
            assertThat(grouped)
                    .extracting(ExtractedToken::text)
                    .contains("com", "example", "Foo");
            assertThat(grouped).allMatch(t -> t.groupId().equals(grouped.get(0).groupId()));
        }

        @Test
        void import_fqn_reconstructed_from_grouped_tokens() {
            ParsedFile result = index("import com.example.Foo;");
            var grouped = result.tokens().stream()
                    .filter(t -> t.groupId() != null)
                    .toList();
            String fqn = grouped.stream()
                    .filter(t -> t.kind() == TokenKind.IDENTIFIER
                            || (t.kind() == TokenKind.SEPARATOR && ".".equals(t.text())))
                    .map(ExtractedToken::text)
                    .collect(java.util.stream.Collectors.joining());
            assertThat(fqn).isEqualTo("com.example.Foo");
        }

        @Test
        void wildcard_import_includes_asterisk_in_group() {
            ParsedFile result = index("import com.example.*;");
            var grouped = result.tokens().stream()
                    .filter(t -> t.groupId() != null)
                    .toList();
            assertThat(grouped)
                    .extracting(ExtractedToken::text)
                    .contains("com", "example", "*");
        }

        @Test
        void static_import_assigns_group() {
            ParsedFile result = index("import static com.example.Util.helper;");
            var grouped = result.tokens().stream()
                    .filter(t -> t.groupId() != null)
                    .toList();
            assertThat(grouped)
                    .extracting(ExtractedToken::text)
                    .contains("com", "example", "Util", "helper");
        }

        @Test
        void two_imports_get_different_group_ids() {
            ParsedFile result = index("import com.example.Foo;\nimport com.example.Bar;");
            var groups = result.tokens().stream()
                    .filter(t -> t.groupId() != null)
                    .map(ExtractedToken::groupId)
                    .distinct()
                    .toList();
            assertThat(groups).hasSize(2);
        }

        @Test
        void import_keyword_does_not_get_a_group_id() {
            ParsedFile result = index("import com.example.Foo;");
            var importKeyword = result.tokens().stream()
                    .filter(t -> "import".equals(t.text()) && t.kind() == TokenKind.KEYWORD)
                    .findFirst();
            assertThat(importKeyword).isPresent();
            assertThat(importKeyword.get().groupId()).isNull();
        }
    }

//    @Test
//    void open_document_opens_file_before_readiness_probe() {
//        @SuppressWarnings("unchecked")
//        LanguageServerSession<JdtlsNotifyingLanguageClient> session = mock(LanguageServerSession.class);
//        TextDocumentService textDocumentService = mock(TextDocumentService.class);
//
//        when(session.textDocumentService()).thenReturn(textDocumentService);
//        when(textDocumentService.documentSymbol(any())).thenReturn(CompletableFuture.completedFuture(null));
//
//        JavaAntlrIndexer.openDocument(session, "file:///repo/src/main/java/Example.java", "class Example {}\n");
//
//        InOrder inOrder = inOrder(textDocumentService);
//        inOrder.verify(textDocumentService).didOpen(any());
//        inOrder.verify(textDocumentService).documentSymbol(any());
//    }
//
//    @Test
//    void open_document_retries_readiness_probe_after_failed_attempts() {
//        @SuppressWarnings("unchecked")
//        LanguageServerSession<JdtlsNotifyingLanguageClient> session = mock(LanguageServerSession.class);
//        TextDocumentService textDocumentService = mock(TextDocumentService.class);
//
//        when(session.textDocumentService()).thenReturn(textDocumentService);
//        when(session.textDocumentService()).thenReturn(textDocumentService);
//        when(textDocumentService.documentSymbol(any()))
//                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("not ready")))
//                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("still not ready")))
//                .thenReturn(CompletableFuture.completedFuture(null));
//
//        JavaAntlrIndexer.openDocument(session, "file:///repo/src/main/java/Retry.java", "class Retry {}\n");
//
//        verify(textDocumentService, times(1)).didOpen(any());
//        verify(textDocumentService, times(3)).documentSymbol(any());
//    }

    private ParsedFile index(String source) {
        return indexer.indexFile(FILE_ID, JAVA_PATH, source, null);
    }

    private static void assertContainsTokenKind(ParsedFile result, String text, TokenKind expectedKind) {
        assertThat(result.tokens())
                .as("Expected token '%s' with kind %s", text, expectedKind)
                .anyMatch(t -> text.equals(t.text()) && t.kind() == expectedKind);
    }
}
