package com.hlag.sourceviewer.adapter.incoming.rest.dto;

public record IndexedRepoStatsDto(
        long id,
        String name,
        long fileCount
) {}
