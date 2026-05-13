package com.hlag.sourceviewer.application.scan;

import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.port.incoming.GetFileContentUseCase;
import com.hlag.sourceviewer.domain.port.outgoing.GitAccess;
import com.hlag.sourceviewer.domain.port.outgoing.RepositoryStore;
import com.hlag.sourceviewer.domain.port.outgoing.SourceFileRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;

@ApplicationScoped
public class GetFileContentService implements GetFileContentUseCase {

    private final SourceFileRepository sourceFileRepository;
    private final RepositoryStore repositoryStore;
    private final GitAccess gitAccess;

    @Inject
    public GetFileContentService(
            SourceFileRepository sourceFileRepository,
            RepositoryStore repositoryStore,
            GitAccess gitAccess) {
        this.sourceFileRepository = sourceFileRepository;
        this.repositoryStore = repositoryStore;
        this.gitAccess = gitAccess;
    }

    @Override
    public Optional<String> getFileContent(FileIdentifier fileIdentifier) {
        return sourceFileRepository.findByIdentifier(fileIdentifier).flatMap(sourceFile ->
                repositoryStore.findByIdentifier(sourceFile.repositoryIdentifier())
                        .flatMap(repository -> repository.lastCommitSha()
                                .flatMap(commitSha -> gitAccess.readFileContent(
                                        repository, sourceFile.path(), commitSha)))
        );
    }
}
