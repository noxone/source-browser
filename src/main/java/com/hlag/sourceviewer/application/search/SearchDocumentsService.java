package com.hlag.sourceviewer.application.search;

import com.hlag.sourceviewer.domain.model.identifier.Description;
import com.hlag.sourceviewer.domain.model.search.SearchQuery;
import com.hlag.sourceviewer.domain.model.search.SearchResult;
import com.hlag.sourceviewer.domain.port.incoming.SearchDocumentsUseCase;
import com.hlag.sourceviewer.domain.port.outgoing.DocumentRepository;
import com.hlag.sourceviewer.domain.port.outgoing.RepositoryStore;
import com.hlag.sourceviewer.domain.port.outgoing.SourceFileRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class SearchDocumentsService implements SearchDocumentsUseCase {

    private final DocumentRepository documentRepository;
    private final SourceFileRepository sourceFileRepository;
    private final RepositoryStore repositoryStore;

    @Inject
    public SearchDocumentsService(
            DocumentRepository documentRepository,
            SourceFileRepository sourceFileRepository,
            RepositoryStore repositoryStore) {
        this.documentRepository = documentRepository;
        this.sourceFileRepository = sourceFileRepository;
        this.repositoryStore = repositoryStore;
    }

    @Override
    public List<SearchResult> search(SearchQuery query) {
        return documentRepository.search(query.searchText().value(), query.maxResults(), query.offset())
                .stream()
                .flatMap(match -> sourceFileRepository.findByIdentifier(match.fileIdentifier())
                        .stream()
                        .flatMap(sourceFile -> repositoryStore.findByIdentifier(sourceFile.repositoryIdentifier())
                                .stream()
                                .map(repository -> new SearchResult(
                                        match.fileIdentifier(),
                                        sourceFile.path(),
                                        repository.name(),
                                        new Description(match.snippet()),
                                        match.rank()
                                ))))
                .toList();
    }
}
