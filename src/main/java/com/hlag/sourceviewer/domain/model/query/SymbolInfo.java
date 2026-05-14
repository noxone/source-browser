package com.hlag.sourceviewer.domain.model.query;

public record SymbolInfo(
        Long symbolId,
        Long fileId,
        String filePath,
        String repositoryName,
        String qualifiedName,
        String simpleName,
        String kind,
        String signature,
        Integer lineStart,
        Integer lineEnd
) {}
