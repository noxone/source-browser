package com.hlag.sourceviewer.application.scan.treesitter;

import com.hlag.sourceviewer.application.lsp.LspServerManager;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TypeScriptTreeSitterLspIndexerUnitTest {

    private TypeScriptTreeSitterLspIndexer indexer;

    @BeforeEach
    void setUp() {
        indexer = new TypeScriptTreeSitterLspIndexer(mock(LspServerManager.class));
    }

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
    void handles_ts_and_tsx_files() {
        assertThat(indexer.handles(new FilePath("src/App.ts"))).isTrue();
        assertThat(indexer.handles(new FilePath("src/App.tsx"))).isTrue();
    }

    @Test
    void does_not_handle_java_or_css_files() {
        assertThat(indexer.handles(new FilePath("Foo.java"))).isFalse();
        assertThat(indexer.handles(new FilePath("style.css"))).isFalse();
    }
}
