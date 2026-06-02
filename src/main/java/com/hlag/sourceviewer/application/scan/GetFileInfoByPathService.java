package com.hlag.sourceviewer.application.scan;

import com.hlag.sourceviewer.domain.model.identifier.BranchName;
import com.hlag.sourceviewer.domain.model.identifier.DisplayName;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import com.hlag.sourceviewer.domain.model.query.FileDetails;
import com.hlag.sourceviewer.domain.port.incoming.GetFileInfoByPathUseCase;
import com.hlag.sourceviewer.domain.port.incoming.GetFileInfoUseCase;
import com.hlag.sourceviewer.domain.port.outgoing.RepositoryStore;
import com.hlag.sourceviewer.domain.port.outgoing.SourceFileRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;

@ApplicationScoped
public class GetFileInfoByPathService implements GetFileInfoByPathUseCase {

    private final RepositoryStore repositoryStore;
    private final SourceFileRepository sourceFileRepository;
    private final GetFileInfoUseCase getFileInfoUseCase;

    @Inject
    public GetFileInfoByPathService(
            RepositoryStore repositoryStore,
            SourceFileRepository sourceFileRepository,
            GetFileInfoUseCase getFileInfoUseCase) {
        this.repositoryStore = repositoryStore;
        this.sourceFileRepository = sourceFileRepository;
        this.getFileInfoUseCase = getFileInfoUseCase;
    }

    @Override
    public Optional<FileDetails> getFileInfoByPath(DisplayName repositoryName, FilePath filePath, BranchName branch) {
        return repositoryStore.findByName(repositoryName).flatMap(repository -> {
            BranchName effectiveBranch = (branch != null) ? branch : repository.defaultBranch();
            return sourceFileRepository
                    .findByRepositoryAndPath(repository.identifier(), effectiveBranch, filePath)
                    .flatMap(sourceFile -> getFileInfoUseCase.getFileInfo(sourceFile.identifier()));
        });
    }
}
