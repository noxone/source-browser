package com.hlag.sourceviewer.application.scan.indexer;

import com.hlag.sourceviewer.application.scan.JavaFileParser;
import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.nio.file.Path;
import java.util.List;

/** Fallback Java indexer: uses source roots and JDK reflection only. */
@ApplicationScoped
public class GenericJavaIndexer implements LanguageIndexer {

    private final JavaFileParser javaFileParser;

    @Inject
    public GenericJavaIndexer(JavaFileParser javaFileParser) {
        this.javaFileParser = javaFileParser;
    }

    @Override
    public String supportedLanguage() {
        return "java";
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean analyze(Path repoRoot, List<FilePath> allFiles) {
        return true;
    }

    @Override
    public JavaIndexingContext prepare(Path repoRoot) {
        return new JavaIndexingContext(javaFileParser.buildTypeSolver(repoRoot));
    }

    @Override
    public boolean handles(FilePath path) {
        return path.isJavaFile();
    }

    @Override
    public JavaFileParser.ParsedFile indexFile(FileIdentifier fileId, FilePath path,
                                               String content, Object context) {
        return javaFileParser.parse(fileId, path, content, ((JavaIndexingContext) context).typeSolver());
    }
}
