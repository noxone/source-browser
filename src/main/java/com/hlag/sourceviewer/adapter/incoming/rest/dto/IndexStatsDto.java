package com.hlag.sourceviewer.adapter.incoming.rest.dto;

import java.util.List;

public record IndexStatsDto(
        List<IndexedRepoStatsDto> repositories,
        long totalFiles,
        long totalDocuments,
        long totalSymbols,
        long totalReferences
) {}
