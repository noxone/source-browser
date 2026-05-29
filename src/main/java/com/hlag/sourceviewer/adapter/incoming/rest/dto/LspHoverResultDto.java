package com.hlag.sourceviewer.adapter.incoming.rest.dto;

public record LspHoverResultDto(
        String markdownContent,
        String definitionFilePath,
        Integer definitionLine,
        Integer definitionColumn) {}
