package com.hlag.sourceviewer.application.resolve;

import com.hlag.sourceviewer.adapter.incoming.rest.dto.SymbolDto;
import com.hlag.sourceviewer.adapter.incoming.rest.dto.SymbolReferenceDto;
import com.hlag.sourceviewer.domain.model.identifier.SymbolIdentifier;
import com.hlag.sourceviewer.domain.port.incoming.GetSymbolDetailsUseCase;
import com.hlag.sourceviewer.domain.port.outgoing.RepositoryStore;
import com.hlag.sourceviewer.domain.port.outgoing.SourceFileRepository;
import com.hlag.sourceviewer.domain.port.outgoing.SymbolReferenceRepository;
import com.hlag.sourceviewer.domain.port.outgoing.SymbolRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class GetSymbolDetailsService implements GetSymbolDetailsUseCase {

    private final SymbolRepository symbolRepository;
    private final SymbolReferenceRepository symbolReferenceRepository;
    private final SourceFileRepository sourceFileRepository;
    private final RepositoryStore repositoryStore;

    @Inject
    public GetSymbolDetailsService(
            SymbolRepository symbolRepository,
            SymbolReferenceRepository symbolReferenceRepository,
            SourceFileRepository sourceFileRepository,
            RepositoryStore repositoryStore) {
        this.symbolRepository = symbolRepository;
        this.symbolReferenceRepository = symbolReferenceRepository;
        this.sourceFileRepository = sourceFileRepository;
        this.repositoryStore = repositoryStore;
    }

    @Override
    public Optional<SymbolDto> getSymbol(SymbolIdentifier symbolIdentifier) {
        return symbolRepository.findByIdentifier(symbolIdentifier).map(symbol -> {
            var sourceFile = sourceFileRepository.findByIdentifier(symbol.fileIdentifier()).orElse(null);
            String filePath = sourceFile != null ? sourceFile.path().value() : null;
            String repoName = null;
            if (sourceFile != null) {
                repoName = repositoryStore.findByIdentifier(sourceFile.repositoryIdentifier())
                        .map(r -> r.name().value()).orElse(null);
            }
            return new SymbolDto(
                    symbol.identifier().value(),
                    symbol.fileIdentifier().value(),
                    filePath,
                    repoName,
                    symbol.qualifiedName().value(),
                    symbol.name().value(),
                    symbol.kind().name(),
                    symbol.signature().map(s -> s.value()).orElse(null),
                    symbol.lineStart().map(l -> l.value()).orElse(null),
                    symbol.lineEnd().map(l -> l.value()).orElse(null)
            );
        });
    }

    @Override
    public List<SymbolReferenceDto> getReferences(SymbolIdentifier symbolIdentifier) {
        return symbolReferenceRepository.findBySymbol(symbolIdentifier).stream().map(ref -> {
            var sourceFile = sourceFileRepository.findByIdentifier(ref.fileIdentifier()).orElse(null);
            String filePath = sourceFile != null ? sourceFile.path().value() : null;
            String repoName = null;
            if (sourceFile != null) {
                repoName = repositoryStore.findByIdentifier(sourceFile.repositoryIdentifier())
                        .map(r -> r.name().value()).orElse(null);
            }
            return new SymbolReferenceDto(
                    ref.identifier() != null ? ref.identifier().value() : null,
                    ref.fileIdentifier().value(),
                    filePath,
                    repoName,
                    ref.kind().name(),
                    ref.line().map(l -> l.value()).orElse(null),
                    ref.columnStart().map(c -> c.value()).orElse(null)
            );
        }).toList();
    }
}
