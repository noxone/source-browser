package com.hlag.sourceviewer.application.scan.indexer;

import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LanguageIndexerRegistryUnitTest {

    private static final Path REPO_ROOT = Path.of("/repo");
    private static final List<FilePath> FILES = List.of(new FilePath("Foo.java"));

    private LanguageIndexerRegistry registryWith(LanguageIndexer... indexers) {
        Instance<LanguageIndexer> instance = mock();
        when(instance.spliterator()).thenReturn(
                Spliterators.spliteratorUnknownSize(List.of(indexers).iterator(),
                        Spliterator.ORDERED));
        return new LanguageIndexerRegistry(instance);
    }

    @Test
    void selects_higher_priority_indexer_when_both_analyze_returns_true() {
        var high = mockIndexer("java", 10, true);
        var low = mockIndexer("java", 100, true);
        var registry = registryWith(low, high);

        Map<String, SelectedIndexerContext> result = registry.selectAndPrepare(REPO_ROOT, FILES);

        assertThat(result).containsKey("java");
        verify(high).prepare(REPO_ROOT);
        verify(low, never()).prepare(any());
    }

    @Test
    void falls_back_to_lower_priority_when_higher_priority_analyze_returns_false() {
        var high = mockIndexer("java", 10, false);
        var low = mockIndexer("java", 100, true);
        var registry = registryWith(high, low);

        Map<String, SelectedIndexerContext> result = registry.selectAndPrepare(REPO_ROOT, FILES);

        assertThat(result).containsKey("java");
        verify(low).prepare(REPO_ROOT);
        verify(high, never()).prepare(any());
    }

    @Test
    void returns_empty_when_no_indexer_applies() {
        var indexer = mockIndexer("java", 100, false);
        var registry = registryWith(indexer);

        Map<String, SelectedIndexerContext> result = registry.selectAndPrepare(REPO_ROOT, FILES);

        assertThat(result).isEmpty();
    }

    @Test
    void selects_independently_for_different_languages() {
        var javaIndexer = mockIndexer("java", 100, true);
        var kotlinIndexer = mockIndexer("kotlin", 100, true);
        var registry = registryWith(javaIndexer, kotlinIndexer);

        Map<String, SelectedIndexerContext> result = registry.selectAndPrepare(REPO_ROOT, FILES);

        assertThat(result).containsKeys("java", "kotlin");
    }

    private LanguageIndexer mockIndexer(String language, int priority, boolean analyzeResult) {
        LanguageIndexer indexer = mock(LanguageIndexer.class);
        when(indexer.supportedLanguage()).thenReturn(language);
        when(indexer.priority()).thenReturn(priority);
        when(indexer.analyze(any(), any())).thenReturn(analyzeResult);
        when(indexer.prepare(any())).thenReturn(null);
        return indexer;
    }
}
