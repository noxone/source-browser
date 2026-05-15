package com.hlag.sourceviewer.application.scan;

import com.hlag.sourceviewer.domain.model.query.FileDetails;
import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.port.incoming.GetFileInfoUseCase;
import com.hlag.sourceviewer.domain.port.outgoing.GitAccess;
import com.hlag.sourceviewer.domain.port.outgoing.RepositoryStore;
import com.hlag.sourceviewer.domain.port.outgoing.SourceFileRepository;
import com.hlag.sourceviewer.domain.port.outgoing.TokenStreamRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;

@ApplicationScoped
public class GetFileInfoService implements GetFileInfoUseCase {

    private final SourceFileRepository sourceFileRepository;
    private final RepositoryStore repositoryStore;
    private final TokenStreamRepository tokenStreamRepository;
    private final GitAccess gitAccess;

    @Inject
    public GetFileInfoService(
            SourceFileRepository sourceFileRepository,
            RepositoryStore repositoryStore,
            TokenStreamRepository tokenStreamRepository,
            GitAccess gitAccess) {
        this.sourceFileRepository = sourceFileRepository;
        this.repositoryStore = repositoryStore;
        this.tokenStreamRepository = tokenStreamRepository;
        this.gitAccess = gitAccess;
    }

    @Override
    public Optional<FileDetails> getFileInfo(FileIdentifier fileIdentifier) {
        return sourceFileRepository.findByIdentifier(fileIdentifier).map(sourceFile -> {
            var repository = repositoryStore.findByIdentifier(sourceFile.repositoryIdentifier())
                    .orElse(null);

            boolean hasTokenStream = tokenStreamRepository.findByFileId(fileIdentifier).isPresent();

            String repoName = repository != null ? repository.name().value() : null;
            String lastCommitSha = null;
            String lastAuthorName = null;
            String lastAuthorEmail = null;
            java.time.Instant lastCommitDate = null;
            String lastCommitMessage = null;

            Long fileSize = null;
            String repositoryUrl = null;
            if (repository != null) {
                var commitInfo = gitAccess.getLastCommitForFile(
                        repository, sourceFile.path(), sourceFile.branch());
                if (commitInfo.isPresent()) {
                    var info = commitInfo.get();
                    lastCommitSha = info.sha();
                    lastAuthorName = info.authorName();
                    lastAuthorEmail = info.authorEmail();
                    lastCommitDate = info.commitDate();
                    lastCommitMessage = info.message();
                }
                fileSize = gitAccess.getFileSizeForFile(repository, sourceFile.path(), sourceFile.branch()).orElse(null);
                repositoryUrl = repository.remoteUrl().map(url -> url.value()).orElse(null);
            }

            return new FileDetails(
                    fileIdentifier.value(),
                    sourceFile.path().value(),
                    repoName,
                    sourceFile.branch().value(),
                    sourceFile.language().value(),
                    sourceFile.indexedAt(),
                    hasTokenStream,
                    lastCommitSha,
                    lastAuthorName,
                    lastAuthorEmail,
                    lastCommitDate,
                    lastCommitMessage,
                    fileSize,
                    repositoryUrl
            );
        });
    }
}
