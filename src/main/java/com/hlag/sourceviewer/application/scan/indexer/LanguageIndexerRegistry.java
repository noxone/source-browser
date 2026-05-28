package com.hlag.sourceviewer.application.scan.indexer;

import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import com.hlag.sourceviewer.domain.model.repository.Repository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.groupingBy;

/**
 * Selects one {@link LanguageIndexer} per language for a given repository scan.
 *
 * <p>For each language, candidates are sorted by {@link LanguageIndexer#priority()} (ascending)
 * and the first one whose {@link LanguageIndexer#analyze} returns {@code true} is selected
 * and prepared.</p>
 */
@ApplicationScoped
public class LanguageIndexerRegistry {

    private final Instance<LanguageIndexer> indexers;

    @Inject
    public LanguageIndexerRegistry(Instance<LanguageIndexer> indexers) {
        this.indexers = indexers;
    }

    /**
     * Selects one indexer per language, calls {@link LanguageIndexer#prepare} on each
     * selected indexer, and returns the resulting contexts keyed by language token.
     */
    public Map<String, SelectedIndexerContext> selectAndPrepare(Path repoRoot, List<FilePath> allFiles, Repository repository) {
        Map<String, List<LanguageIndexer>> byLanguage = StreamSupport
                .stream(indexers.spliterator(), false)
                .collect(groupingBy(LanguageIndexer::supportedLanguage));

        Map<String, SelectedIndexerContext> result = new LinkedHashMap<>();
        byLanguage.forEach((lang, candidates) ->
                candidates.stream()
                        .sorted(comparingInt(LanguageIndexer::priority))
                        .filter(idx -> idx.analyze(repoRoot, allFiles))
                        .findFirst()
                        .ifPresent(idx -> {
                            Object ctx = idx.prepare(repoRoot, repository);
                            result.put(lang, new SelectedIndexerContext(idx, ctx));
                        }));
        return Collections.unmodifiableMap(result);
    }
}
