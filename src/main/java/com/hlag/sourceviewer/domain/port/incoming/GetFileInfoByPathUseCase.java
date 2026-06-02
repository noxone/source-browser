package com.hlag.sourceviewer.domain.port.incoming;

import com.hlag.sourceviewer.domain.model.identifier.BranchName;
import com.hlag.sourceviewer.domain.model.identifier.DisplayName;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import com.hlag.sourceviewer.domain.model.query.FileDetails;

import java.util.Optional;

public interface GetFileInfoByPathUseCase {

    /**
     * Looks up a file by its symbolic coordinates (repository display name + file path).
     * When {@code branch} is {@code null} the repository's default branch is used.
     */
    Optional<FileDetails> getFileInfoByPath(DisplayName repositoryName, FilePath filePath, BranchName branch);
}
