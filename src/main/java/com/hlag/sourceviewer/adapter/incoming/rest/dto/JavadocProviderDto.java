package com.hlag.sourceviewer.adapter.incoming.rest.dto;

public record JavadocProviderDto(
        Long id,
        String name,
        String packagePrefix,
        String urlTemplate,
        int sortOrder
) {}
