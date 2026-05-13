package com.hlag.sourceviewer.domain.port.incoming;

import com.hlag.sourceviewer.adapter.incoming.rest.dto.SymbolDto;
import com.hlag.sourceviewer.adapter.incoming.rest.dto.SymbolReferenceDto;
import com.hlag.sourceviewer.domain.model.identifier.SymbolIdentifier;

import java.util.List;
import java.util.Optional;

public interface GetSymbolDetailsUseCase {
    Optional<SymbolDto> getSymbol(SymbolIdentifier symbolIdentifier);
    List<SymbolReferenceDto> getReferences(SymbolIdentifier symbolIdentifier);
}
