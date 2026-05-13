package com.hlag.sourceviewer.application.scan.indexer;

import com.hlag.sourceviewer.application.scan.JavaFileParser;
import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;

/** Ties a selected {@link LanguageIndexer} to its prepared per-scan context. */
public record SelectedIndexerContext(LanguageIndexer indexer, Object context) {

    public boolean handles(FilePath path) {
        return indexer.handles(path);
    }

    public JavaFileParser.ParsedFile index(FileIdentifier fileId, FilePath path, String content) {
        return indexer.indexFile(fileId, path, content, context);
    }
}
