package com.hlag.sourceviewer.domain.model.query;

public record SymbolReferenceInfo(
        Long referenceId,
        Long fileId,
        String filePath,
        String repositoryName,
        String kind,
        Integer line,
        Integer columnStart
) {}
