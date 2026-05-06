package com.hlag.sourceviewer.domain.port.outgoing;

import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.ReferenceIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.SymbolIdentifier;
import com.hlag.sourceviewer.domain.model.source.SymbolReference;

import java.util.List;

public interface SymbolReferenceRepository {

    List<SymbolReference> findBySymbol(SymbolIdentifier symbolIdentifier);

    List<SymbolReference> findByFile(FileIdentifier fileIdentifier);

    ReferenceIdentifier insert(SymbolReference symbolReference);

    void deleteByFile(FileIdentifier fileIdentifier);
}
