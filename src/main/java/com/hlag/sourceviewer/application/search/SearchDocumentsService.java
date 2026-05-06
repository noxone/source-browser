package com.hlag.sourceviewer.application.search;

import com.hlag.sourceviewer.domain.model.identifier.Description;
import com.hlag.sourceviewer.domain.model.search.SearchQuery;
import com.hlag.sourceviewer.domain.model.search.SearchResult;
import com.hlag.sourceviewer.domain.port.incoming.SearchDocumentsUseCase;
import com.hlag.sourceviewer.domain.port.outgoing.RepositoryStore;
import com.hlag.sourceviewer.domain.port.outgoing.SourceFileRepository;
import com.hlag.sourceviewer.domain.port.outgoing.SymbolRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class SearchDocumentsService implements SearchDocumentsUseCase {

    private final SymbolRepository symbolRepository;
    private final SourceFileRepository sourceFileRepository;
    private final RepositoryStore repositoryStore;

    @Inject
    public SearchDocumentsService(
            SymbolRepository symbolRepository,
            SourceFileRepository sourceFileRepository,
            RepositoryStore repositoryStore) {
        this.symbolRepository = symbolRepository;
        this.sourceFileRepository = sourceFileRepository;
        this.repositoryStore = repositoryStore;
    }

    @Override
    public List<SearchResult> search(SearchQuery query) {
        return symbolRepository.findBySimpleName(query.searchText())
                .stream()
                .skip(query.offset())
                .limit(query.maxResults())
                .flatMap(symbol -> sourceFileRepository.findByIdentifier(symbol.fileIdentifier())
                        .stream()
                        .flatMap(sourceFile -> repositoryStore.findByIdentifier(sourceFile.repositoryIdentifier())
                                .stream()
                                .map(repository -> new SearchResult(
                                        symbol.fileIdentifier(),
                                        sourceFile.path(),
                                        repository.name(),
                                        new Description(symbol.qualifiedName().value()),
                                        1.0
                                ))))
                .toList();
    }
}
