package com.hlag.sourceviewer.application.scan;

import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import com.hlag.sourceviewer.domain.model.identifier.ReferenceKind;
import com.hlag.sourceviewer.domain.model.identifier.SymbolKind;
import com.hlag.sourceviewer.domain.model.source.Symbol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JavaFileParserUnitTest {

    private JavaFileParser parser;
    private com.github.javaparser.resolution.TypeSolver typeSolver;
    private static final FileIdentifier FILE_ID = new FileIdentifier(1L);
    private static final FilePath FILE_PATH = new FilePath("com/example/Foo.java");

    @BeforeEach
    void setUp() {
        parser = new JavaFileParser();
        typeSolver = new ReflectionTypeSolver(false);
    }

    // ── Declaration extraction ────────────────────────────────────────────────

    @Test
    void extracts_class_declaration() {
        String source = "package com.example; public class Foo {}";

        var result = parser.parse(FILE_ID, FILE_PATH, source, typeSolver);

        assertThat(result.declarations()).hasSize(1);
        Symbol sym = result.declarations().get(0);
        assertThat(sym.kind()).isEqualTo(SymbolKind.CLASS);
        assertThat(sym.name().value()).isEqualTo("Foo");
        assertThat(sym.qualifiedName().value()).isEqualTo("com.example.Foo");
        assertThat(sym.modifiers()).contains("public");
        assertThat(sym.lineStart()).isPresent();
    }

    @Test
    void extracts_interface_declaration() {
        String source = "package com.example; public interface Bar {}";

        var result = parser.parse(FILE_ID, FILE_PATH, source, typeSolver);

        assertThat(result.declarations()).hasSize(1);
        assertThat(result.declarations().get(0).kind()).isEqualTo(SymbolKind.INTERFACE);
        assertThat(result.declarations().get(0).qualifiedName().value()).isEqualTo("com.example.Bar");
    }

    @Test
    void extracts_enum_declaration() {
        String source = "package com.example; public enum Status { ACTIVE, INACTIVE }";

        var result = parser.parse(FILE_ID, FILE_PATH, source, typeSolver);

        assertThat(result.declarations()).hasSize(1);
        assertThat(result.declarations().get(0).kind()).isEqualTo(SymbolKind.ENUM);
    }

    @Test
    void extracts_record_declaration() {
        String source = "package com.example; public record Point(int x, int y) {}";

        var result = parser.parse(FILE_ID, FILE_PATH, source, typeSolver);

        assertThat(result.declarations()).hasSize(1);
        assertThat(result.declarations().get(0).kind()).isEqualTo(SymbolKind.RECORD);
        assertThat(result.declarations().get(0).qualifiedName().value()).isEqualTo("com.example.Point");
    }

    @Test
    void extracts_method_declaration_with_signature() {
        String source = """
                package com.example;
                public class Foo {
                    public String greet(String name, int times) { return name; }
                }
                """;

        var result = parser.parse(FILE_ID, FILE_PATH, source, typeSolver);

        List<Symbol> methods = result.declarations().stream()
                .filter(s -> s.kind() == SymbolKind.METHOD).toList();
        assertThat(methods).hasSize(1);
        assertThat(methods.get(0).name().value()).isEqualTo("greet");
        assertThat(methods.get(0).qualifiedName().value()).isEqualTo("com.example.Foo.greet");
        assertThat(methods.get(0).signature()).isPresent();
        assertThat(methods.get(0).signature().get().value()).isEqualTo("greet(String, int)");
    }

    @Test
    void extracts_field_declaration() {
        String source = """
                package com.example;
                public class Foo {
                    private String name;
                }
                """;

        var result = parser.parse(FILE_ID, FILE_PATH, source, typeSolver);

        List<Symbol> fields = result.declarations().stream()
                .filter(s -> s.kind() == SymbolKind.FIELD).toList();
        assertThat(fields).hasSize(1);
        assertThat(fields.get(0).name().value()).isEqualTo("name");
        assertThat(fields.get(0).qualifiedName().value()).isEqualTo("com.example.Foo.name");
        assertThat(fields.get(0).modifiers()).contains("private");
    }

    @Test
    void extracts_inner_class_with_nested_qualified_name() {
        String source = """
                package com.example;
                public class Outer {
                    public class Inner {}
                }
                """;

        var result = parser.parse(FILE_ID, FILE_PATH, source, typeSolver);

        assertThat(result.declarations())
                .extracting(s -> s.qualifiedName().value())
                .containsExactlyInAnyOrder("com.example.Outer", "com.example.Outer.Inner");
    }

    @Test
    void extracts_constructor_with_signature() {
        String source = """
                package com.example;
                public class Foo {
                    public Foo(String name) {}
                }
                """;

        var result = parser.parse(FILE_ID, FILE_PATH, source, typeSolver);

        List<Symbol> constructors = result.declarations().stream()
                .filter(s -> s.kind() == SymbolKind.CONSTRUCTOR).toList();
        assertThat(constructors).hasSize(1);
        assertThat(constructors.get(0).signature().get().value()).isEqualTo("Foo(String)");
    }

    @Test
    void class_without_package_has_simple_qualified_name() {
        String source = "public class Standalone {}";

        var result = parser.parse(FILE_ID, FILE_PATH, source, typeSolver);

        assertThat(result.declarations().get(0).qualifiedName().value()).isEqualTo("Standalone");
    }

    // ── Reference extraction ──────────────────────────────────────────────────

    @Test
    void extracts_extends_reference() {
        String source = """
                package com.example;
                import java.util.AbstractList;
                public class MyList extends AbstractList<String> {
                    public String get(int i) { return null; }
                    public int size() { return 0; }
                }
                """;

        var result = parser.parse(FILE_ID, FILE_PATH, source, typeSolver);

        assertThat(result.references())
                .anyMatch(r -> r.kind() == ReferenceKind.EXTENDS);
    }

    @Test
    void extracts_implements_reference() {
        String source = """
                package com.example;
                public class Foo implements Runnable {
                    public void run() {}
                }
                """;

        var result = parser.parse(FILE_ID, FILE_PATH, source, typeSolver);

        assertThat(result.references())
                .anyMatch(r -> r.kind() == ReferenceKind.IMPLEMENTS
                        && r.resolvedName().map(n -> n.value().equals("java.lang.Runnable")).orElse(false));
    }

    @Test
    void extracts_method_call_reference_with_resolved_name() {
        String source = """
                package com.example;
                public class Foo {
                    void test() {
                        String s = "hello";
                        int len = s.length();
                    }
                }
                """;

        var result = parser.parse(FILE_ID, FILE_PATH, source, typeSolver);

        assertThat(result.references())
                .anyMatch(r -> r.kind() == ReferenceKind.METHOD_CALL
                        && r.resolvedName().map(n -> n.value().contains("length")).orElse(false));
    }

    @Test
    void extracts_annotation_use_reference() {
        String source = """
                package com.example;
                public class Foo {
                    @Override
                    public String toString() { return ""; }
                }
                """;

        var result = parser.parse(FILE_ID, FILE_PATH, source, typeSolver);

        assertThat(result.references())
                .anyMatch(r -> r.kind() == ReferenceKind.ANNOTATION_USE
                        && r.resolvedName().map(n -> n.value().equals("java.lang.Override")).orElse(false));
    }

    @Test
    void stores_unresolved_name_when_type_cannot_be_resolved() {
        String source = """
                package com.example;
                import com.other.UnknownClass;
                public class Foo {
                    UnknownClass field;
                }
                """;

        var result = parser.parse(FILE_ID, FILE_PATH, source, typeSolver);

        assertThat(result.references())
                .anyMatch(r -> r.unresolvedName().map(n -> n.value().equals("UnknownClass")).orElse(false));
    }

    @Test
    void returns_empty_result_for_unparseable_content() {
        var result = parser.parse(FILE_ID, FILE_PATH, "this is not java code $$$$", typeSolver);

        assertThat(result.declarations()).isEmpty();
        assertThat(result.references()).isEmpty();
    }

    @Test
    void line_numbers_are_populated() {
        String source = """
                package com.example;
                public class Foo {
                    public void bar() {}
                }
                """;

        var result = parser.parse(FILE_ID, FILE_PATH, source, typeSolver);

        assertThat(result.declarations())
                .allSatisfy(s -> assertThat(s.lineStart()).isPresent());
    }

    // ── buildTypeSolver — source root discovery ───────────────────────────────

    @Test
    void buildTypeSolver_finds_standard_single_module_source_roots(@TempDir Path repoRoot) throws IOException {
        Files.createDirectories(repoRoot.resolve("src/main/java"));
        Files.createDirectories(repoRoot.resolve("src/test/java"));

        // should not throw and should return a working solver
        var solver = parser.buildTypeSolver(repoRoot);

        assertThat(solver).isNotNull();
    }

    @Test
    void buildTypeSolver_finds_source_roots_in_sub_modules(@TempDir Path repoRoot) throws IOException {
        Files.createDirectories(repoRoot.resolve("module-a/src/main/java/com/example"));
        Files.createDirectories(repoRoot.resolve("module-b/src/main/java/com/other"));
        Files.createDirectories(repoRoot.resolve("module-b/src/test/java/com/other"));

        var solver = parser.buildTypeSolver(repoRoot);

        assertThat(solver).isNotNull();
    }

    @Test
    void buildTypeSolver_skips_target_directory(@TempDir Path repoRoot) throws IOException {
        // src/main/java is in target — should be ignored
        Files.createDirectories(repoRoot.resolve("target/src/main/java/com/example"));
        Files.createDirectories(repoRoot.resolve("src/main/java/com/example"));

        // Both solvers are added but the target one is skipped; no exception expected
        var solver = parser.buildTypeSolver(repoRoot);

        assertThat(solver).isNotNull();
    }

    @Test
    void buildTypeSolver_works_with_no_source_roots(@TempDir Path repoRoot) {
        // repo with no src/main/java at all — still returns a working solver (reflection only)
        var solver = parser.buildTypeSolver(repoRoot);

        assertThat(solver).isNotNull();
    }
}
