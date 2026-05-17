package com.hlag.sourceviewer.adapter.incoming.rest.dto;

public record CreateJavadocProviderDto(
        String name,
        String packagePrefix,
        String urlTemplate,
        int sortOrder
) {}
