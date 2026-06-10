package com.hlag.sourceviewer.application.search;

import com.hlag.sourceviewer.domain.port.incoming.GetIndexStatsUseCase;
import com.hlag.sourceviewer.domain.port.outgoing.RepositoryStore;
import com.hlag.sourceviewer.domain.port.outgoing.SourceFileRepository;
import com.hlag.sourceviewer.domain.port.outgoing.SymbolReferenceRepository;
import com.hlag.sourceviewer.domain.port.outgoing.SymbolRepository;
import com.hlag.sourceviewer.domain.port.outgoing.TokenDetailRepository;
import com.hlag.sourceviewer.domain.port.outgoing.TypeHierarchyRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class GetIndexStatsService implements GetIndexStatsUseCase {

    private final RepositoryStore repositoryStore;
    private final SourceFileRepository sourceFileRepository;
    private final SymbolRepository symbolRepository;
    private final SymbolReferenceRepository symbolReferenceRepository;
    private final TokenDetailRepository tokenDetailRepository;
    private final TypeHierarchyRepository typeHierarchyRepository;

    @Inject
    public GetIndexStatsService(
            RepositoryStore repositoryStore,
            SourceFileRepository sourceFileRepository,
            SymbolRepository symbolRepository,
            SymbolReferenceRepository symbolReferenceRepository,
            TokenDetailRepository tokenDetailRepository,
            TypeHierarchyRepository typeHierarchyRepository) {
        this.repositoryStore = repositoryStore;
        this.sourceFileRepository = sourceFileRepository;
        this.symbolRepository = symbolRepository;
        this.symbolReferenceRepository = symbolReferenceRepository;
        this.tokenDetailRepository = tokenDetailRepository;
        this.typeHierarchyRepository = typeHierarchyRepository;
    }

    @Override
    public IndexStats getStats() {
        Map<Long, Long> fileCounts = sourceFileRepository.countPerRepository();

        List<RepoStats> repoStats = repositoryStore.findAll().stream()
                .filter(repo -> fileCounts.containsKey(repo.identifier().value()))
                .map(repo -> new RepoStats(
                        repo.identifier().value(),
                        repo.name().value(),
                        fileCounts.get(repo.identifier().value())))
                .sorted(Comparator.comparing(RepoStats::name))
                .toList();

        return new IndexStats(
                repoStats,
                sourceFileRepository.countAll(),
                symbolRepository.countPublished(),
                symbolReferenceRepository.countPublished(),
                tokenDetailRepository.countPublished(),
                typeHierarchyRepository.countPublished());
    }
}
