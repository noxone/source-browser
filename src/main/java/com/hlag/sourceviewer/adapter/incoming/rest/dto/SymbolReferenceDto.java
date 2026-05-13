package com.hlag.sourceviewer.adapter.incoming.rest.dto;

public record SymbolReferenceDto(
        Long referenceId,
        Long fileId,
        String filePath,
        String repositoryName,
        String kind,
        Integer line,
        Integer columnStart
) {}
