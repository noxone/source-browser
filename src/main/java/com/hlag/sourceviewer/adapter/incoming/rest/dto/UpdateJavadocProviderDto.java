package com.hlag.sourceviewer.adapter.incoming.rest.dto;

public record UpdateJavadocProviderDto(
        String name,
        String packagePrefix,
        String urlTemplate,
        int sortOrder
) {}
