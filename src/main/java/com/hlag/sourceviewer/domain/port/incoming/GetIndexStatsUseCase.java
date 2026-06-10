package com.hlag.sourceviewer.domain.port.incoming;

import java.util.List;

public interface GetIndexStatsUseCase {

    record RepoStats(long id, String name, long fileCount) {}

    record IndexStats(
            List<RepoStats> repositories,
            long totalFiles,
            long totalSymbols,
            long totalReferences,
            long totalTokenInfos,
            long totalTypeHierarchies
    ) {}

    IndexStats getStats();
}
