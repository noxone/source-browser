package com.hlag.sourceviewer.application.scan.treesitter;

import com.hlag.sourceviewer.application.lsp.LspServerManager;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class JavaTreeSitterLspIndexerUnitTest {

    private JavaTreeSitterLspIndexer indexer;

    @BeforeEach
    void setUp() {
        indexer = new JavaTreeSitterLspIndexer(mock(LspServerManager.class));
    }

    @Test
    void supported_language_is_java() {
        assertThat(indexer.supportedLanguage()).isEqualTo("java");
    }

    @Test
    void priority_is_10() {
        assertThat(indexer.priority()).isEqualTo(10);
    }

    @Test
    void analyze_returns_native_availability() {
        assertThat(indexer.analyze(Path.of("/any"), List.of()))
                .isEqualTo(AbstractTreeSitterLspIndexer.NATIVE_AVAILABLE);
    }

    @Test
    void handles_java_files() {
        assertThat(indexer.handles(new FilePath("src/main/java/Foo.java"))).isTrue();
    }

    @Test
    void does_not_handle_non_java_files() {
        assertThat(indexer.handles(new FilePath("src/main/resources/app.properties"))).isFalse();
        assertThat(indexer.handles(new FilePath("frontend/src/App.ts"))).isFalse();
    }
}
