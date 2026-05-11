package com.hlag.sourceviewer.adapter.incoming.rest.dto;

public record SearchResultDto(
        Long fileId,
        String filePath,
        String repositoryName,
        String snippet,
        double relevanceScore
) {}
