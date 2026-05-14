package com.hlag.sourceviewer.domain.port.incoming;

import com.hlag.sourceviewer.domain.model.query.SymbolInfo;
import com.hlag.sourceviewer.domain.model.query.SymbolReferenceInfo;
import com.hlag.sourceviewer.domain.model.identifier.SymbolIdentifier;

import java.util.List;
import java.util.Optional;

public interface GetSymbolDetailsUseCase {
    Optional<SymbolInfo> getSymbol(SymbolIdentifier symbolIdentifier);
    List<SymbolReferenceInfo> getReferences(SymbolIdentifier symbolIdentifier);
}
