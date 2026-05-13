package com.hlag.sourceviewer.adapter.incoming.rest.dto;

public record SymbolDto(
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
