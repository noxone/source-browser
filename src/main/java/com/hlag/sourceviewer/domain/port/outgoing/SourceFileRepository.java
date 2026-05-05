package com.hlag.sourceviewer.domain.port.outgoing;

import com.hlag.sourceviewer.domain.model.identifier.BranchName;
import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import com.hlag.sourceviewer.domain.model.identifier.RepositoryIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.*;
import com.hlag.sourceviewer.domain.model.source.SourceFile;
import com.hlag.sourceviewer.domain.model.repository.ContentSha;

import java.util.List;
import java.util.Optional;

/**
 * Port for accessing persisted source file metadata.
 */
public interface SourceFileRepository {

    Optional<SourceFile> findByIdentifier(FileIdentifier identifier);

    Optional<SourceFile> findByRepositoryAndPath(
            RepositoryIdentifier repositoryIdentifier,
            BranchName branch,
            FilePath path);

    List<SourceFile> findByRepository(RepositoryIdentifier repositoryIdentifier, BranchName branch);

    FileIdentifier insert(SourceFile sourceFile);

    void updateContentSha(FileIdentifier identifier, ContentSha contentSha);

    void deleteByIdentifier(FileIdentifier identifier);
}
