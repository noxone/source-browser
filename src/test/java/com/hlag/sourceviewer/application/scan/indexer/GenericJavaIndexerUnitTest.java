package com.hlag.sourceviewer.application.scan.indexer;

import com.hlag.sourceviewer.application.scan.JavaFileParser;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import com.hlag.sourceviewer.domain.model.repository.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GenericJavaIndexerUnitTest {

    private JavaFileParser javaFileParser;
    private GenericJavaIndexer indexer;

    @BeforeEach
    void setUp() {
        javaFileParser = mock(JavaFileParser.class);
        indexer = new GenericJavaIndexer(javaFileParser);
    }

    @Test
    void analyze_always_returns_true() {
        assertThat(indexer.analyze(Path.of("/any"), List.of())).isTrue();
    }

    @Test
    void handles_returns_true_for_java_file() {
        assertThat(indexer.handles(new FilePath("Foo.java"))).isTrue();
    }

    @Test
    void handles_returns_false_for_non_java_file() {
        assertThat(indexer.handles(new FilePath("README.md"))).isFalse();
    }

    @Test
    void priority_is_100() {
        assertThat(indexer.priority()).isEqualTo(100);
    }

    @Test
    void supported_language_is_java() {
        assertThat(indexer.supportedLanguage()).isEqualTo("java");
    }

    @Test
    void prepare_delegates_to_javaFileParser_buildTypeSolver() {
        var path = Path.of("/repo");
        var repository = mock(Repository.class);
        var solver = mock(com.github.javaparser.resolution.TypeSolver.class);
        when(javaFileParser.buildTypeSolver(path)).thenReturn(solver);

        Object ctx = indexer.prepare(path, repository);

        assertThat(ctx).isInstanceOf(JavaIndexingContext.class);
        assertThat(((JavaIndexingContext) ctx).typeSolver()).isSameAs(solver);
        verify(javaFileParser).buildTypeSolver(path);
    }
}
