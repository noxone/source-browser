package com.hlag.sourceviewer.adapter.incoming.view.dto;

import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.source.Symbol;

import java.util.List;

public record SourceFileViewDto(FileIdentifier fileIdentifier, List<Symbol> symbols) {}
