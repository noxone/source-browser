package com.hlag.sourceviewer.adapter.outgoing.persistence.mapping;

import com.hlag.sourceviewer.domain.model.identifier.Description;
import com.hlag.sourceviewer.domain.model.repository.Repository;
import com.hlag.sourceviewer.domain.model.search.SearchResult;
import com.hlag.sourceviewer.domain.model.source.SourceFile;
import org.mapstruct.Mapper;

@Mapper(componentModel = "cdi")
public interface SourceFileMapper {

    default SearchResult toSearchResult(SourceFile sourceFile, Repository repository) {
        return new SearchResult(
                sourceFile.identifier(),
                sourceFile.path(),
                repository.name(),
                new Description(sourceFile.path().value()),
                1.0
        );
    }
}
