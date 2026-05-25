package com.hlag.sourceviewer.application.scan.treesitter;

import ch.usi.si.seart.treesitter.Language;
import ch.usi.si.seart.treesitter.Node;
import com.hlag.sourceviewer.application.lsp.LspServerManager;
import com.hlag.sourceviewer.application.lsp.LspServerProcess;
import com.hlag.sourceviewer.application.scan.ParsedFile;
import com.hlag.sourceviewer.application.scan.indexer.LanguageIndexer;
import com.hlag.sourceviewer.domain.model.identifier.ColumnNumber;
import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import com.hlag.sourceviewer.domain.model.identifier.LineNumber;
import com.hlag.sourceviewer.domain.model.identifier.QualifiedName;
import com.hlag.sourceviewer.domain.model.identifier.SimpleName;
import com.hlag.sourceviewer.domain.model.identifier.SymbolKind;
import com.hlag.sourceviewer.domain.model.source.ExtractedToken;
import com.hlag.sourceviewer.domain.model.source.Symbol;
import org.eclipse.lsp4j.DocumentSymbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Base class for language indexers that use TreeSitter for tokenisation and
 * an LSP server for symbol extraction.
 *
 * <p>Subclasses supply:</p>
 * <ul>
 *   <li>{@link #loadLanguage()} — the TreeSitter grammar</li>
 *   <li>{@link #mapNodeKind(Node)} — TreeSitter node → {@link ExtractedToken.TokenKind}</li>
 * </ul>
 *
 * <p>During scanning ({@link #indexFile}) the class:</p>
 * <ol>
 *   <li>Tokenises the file with TreeSitter via {@link TreeSitterTokenizer}.</li>
 *   <li>Queries the LSP for {@code textDocument/documentSymbol} to obtain declarations.</li>
 *   <li>Returns a {@link ParsedFile} with combined tokens and symbols.</li>
 * </ol>
 *
 * <p>Concrete subclasses must be {@code @ApplicationScoped} CDI beans.</p>
 */
public abstract class AbstractTreeSitterLspIndexer implements LanguageIndexer {

    private static final Logger logger = LoggerFactory.getLogger(AbstractTreeSitterLspIndexer.class);

    protected final LspServerManager lspServerManager;

    protected AbstractTreeSitterLspIndexer(LspServerManager lspServerManager) {
        this.lspServerManager = lspServerManager;
    }

    /** Returns the TreeSitter grammar for this language. */
    protected abstract Language loadLanguage();

    /**
     * Maps a TreeSitter leaf node to a {@link ExtractedToken.TokenKind} for syntax highlighting.
     * Anonymous nodes (keywords, operators, separators) and named leaf nodes are both provided.
     */
    protected abstract ExtractedToken.TokenKind mapNodeKind(Node node);

    @Override
    public boolean analyze(Path repoRoot, List<FilePath> allFiles) {
        return true;
    }

    /**
     * Starts (or re-uses) the LSP server for this language and workspace.
     * Returns the {@link LspServerProcess} as context; may be {@code null} if the
     * LSP cannot be started (scanning continues with tokens only).
     */
    @Override
    public Object prepare(Path repoRoot) {
        return lspServerManager.getOrCreate(supportedLanguage(), repoRoot).orElse(null);
    }

    @Override
    public ParsedFile indexFile(FileIdentifier fileId, FilePath path, String content, Object context) {
        Language language = loadLanguage();
        List<ExtractedToken> tokens = TreeSitterTokenizer.tokenize(language, content, this::mapNodeKind);

        LspServerProcess lsp = (LspServerProcess) context;
        List<Symbol> symbols = extractSymbols(lsp, fileId, path, content);

        return new ParsedFile(symbols, List.of(), tokens);
    }

    private List<Symbol> extractSymbols(LspServerProcess lsp, FileIdentifier fileId,
                                         FilePath path, String content) {
        if (lsp == null || !lsp.isAlive()) {
            return List.of();
        }
        String uri = toFileUri(lsp.workspacePath(), path);
        List<DocumentSymbol> lspSymbols = lsp.documentSymbols(uri, content);
        List<Symbol> result = new ArrayList<>();
        flattenSymbols(lspSymbols, fileId, path, result);
        return result;
    }

    private void flattenSymbols(List<DocumentSymbol> lspSymbols, FileIdentifier fileId,
                                  FilePath path, List<Symbol> result) {
        for (DocumentSymbol ds : lspSymbols) {
            mapToSymbol(ds, fileId, path).ifPresent(result::add);
            if (ds.getChildren() != null) {
                flattenSymbols(ds.getChildren(), fileId, path, result);
            }
        }
    }

    private Optional<Symbol> mapToSymbol(DocumentSymbol ds, FileIdentifier fileId, FilePath path) {
        if (ds.getName() == null || ds.getName().isBlank()) {
            return Optional.empty();
        }
        SymbolKind domainKind = mapLspSymbolKind(ds.getKind());
        if (domainKind == null) {
            return Optional.empty();
        }

        String qualifiedName = buildQualifiedName(ds, path);
        int startLine = ds.getSelectionRange().getStart().getLine() + 1;
        int startCol  = ds.getSelectionRange().getStart().getCharacter() + 1;
        int endLine   = ds.getRange().getEnd().getLine() + 1;

        return Optional.of(new Symbol(
                fileId,
                domainKind,
                new SimpleName(ds.getName()),
                new QualifiedName(qualifiedName),
                Optional.ofNullable(ds.getDetail()).filter(d -> !d.isBlank()).map(SimpleName::new),
                Optional.of(new LineNumber(startLine)),
                Optional.of(new LineNumber(endLine)),
                Optional.of(new ColumnNumber(startCol)),
                List.of()));
    }

    private static String buildQualifiedName(DocumentSymbol ds, FilePath path) {
        String fileStem = fileStem(path);
        return fileStem + "." + ds.getName();
    }

    private static SymbolKind mapLspSymbolKind(org.eclipse.lsp4j.SymbolKind lspKind) {
        if (lspKind == null) return null;
        return switch (lspKind) {
            case Class       -> SymbolKind.CLASS;
            case Interface   -> SymbolKind.INTERFACE;
            case Enum        -> SymbolKind.ENUM;
            case Method,
                 Function    -> SymbolKind.METHOD;
            case Constructor -> SymbolKind.CONSTRUCTOR;
            case Field,
                 Property,
                 EnumMember  -> SymbolKind.FIELD;
            default          -> null;
        };
    }

    private static String fileStem(FilePath path) {
        String name = Path.of(path.value()).getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot == -1 ? name : name.substring(0, dot);
    }

    private static String toFileUri(Path workspacePath, FilePath repoRelativePath) {
        return workspacePath.resolve(repoRelativePath.value()).toUri().toString();
    }
}
