package com.hlag.sourceviewer.application.scan.indexer;

import com.hlag.sourceviewer.application.scan.ParsedFile;
import com.hlag.sourceviewer.application.scan.lsp.DiagnosticsCapable;
import com.hlag.sourceviewer.application.scan.lsp.LanguageServerSession;
import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import com.hlag.sourceviewer.domain.model.repository.Repository;
import com.hlag.sourceviewer.domain.model.source.ExtractedToken;
import com.hlag.sourceviewer.domain.model.source.Symbol;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Base class for language indexers that use an ANTLR4 lexer for tokenisation.
 *
 * <p>Subclasses supply a language-specific lexer via {@link #createLexer(CharStream)} and
 * a token-type mapping via {@link #mapTokenKind(int)}. Optionally they can override
 * {@link #extractSymbols(List, FilePath, FileIdentifier)} to extract symbol declarations
 * by scanning the flat token list.</p>
 *
 * <p>This class is not annotated with {@code @ApplicationScoped} — concrete subclasses
 * bear that annotation. The default implementations of {@link #analyze} and
 * {@link #prepare} are no-ops: all filtering is done in {@link #handles(FilePath)}.</p>
 */
public abstract class AbstractAntlr4Indexer implements LanguageIndexer {

    private static final Logger logger = LoggerFactory.getLogger(AbstractAntlr4Indexer.class);

    @Override
    public boolean analyze(Path repoRoot, List<FilePath> allFiles) {
        return true;
    }

    @Override
    public Object prepare(Path repoRoot, Repository repository) {
        return null;
    }

    /**
     * Creates the language-specific ANTLR4 lexer for the given character stream.
     *
     * @param input character stream from the file content
     * @return a freshly created, unconfigured lexer ready to tokenise
     */
    protected abstract Lexer createLexer(CharStream input);

    /**
     * Maps an ANTLR4 token type to the display kind used for syntax highlighting.
     *
     * @param tokenType the integer token type as returned by {@link Token#getType()}
     * @return the corresponding {@link ExtractedToken.TokenKind}; never {@code null}
     */
    protected abstract ExtractedToken.TokenKind mapTokenKind(int tokenType);

    /**
     * Extracts symbol declarations by scanning the flat token list.
     *
     * <p>The default implementation returns an empty list. Subclasses that support symbol
     * extraction override this method. The {@code tokens} list contains every token
     * produced by the lexer, including whitespace and comments, in source order.
     * EOF tokens are already excluded.</p>
     *
     * @param tokens  all tokens from the file, excluding EOF
     * @param path    repository-relative path, useful for building qualified names
     * @param fileId  the file's persisted identifier, needed to construct {@link Symbol} instances
     * @return extracted symbol declarations; may be empty, never {@code null}
     */
    protected List<Symbol> extractSymbols(List<Token> tokens, FilePath path, FileIdentifier fileId) {
        return List.of();
    }

    /**
     * Executes {@code action} inside a single LSP {@code didOpen}/{@code didClose} window.
     *
     * <p>Opens the document, waits for the server to publish diagnostics, invokes
     * {@code action}, then closes the document in a {@code finally} block so the window
     * is always released even if {@code action} throws.</p>
     *
     * @param session                  the active language-server session
     * @param fileUri                  LSP URI of the file to open (e.g. {@code file:///…})
     * @param languageId               LSP language identifier (e.g. {@code "java"}, {@code "typescript"})
     * @param content                  full source content to send with {@code didOpen}
     * @param diagnosticsTimeoutSeconds how long to wait for the server's initial diagnostics
     * @param action                   the work to perform while the document is open
     * @return the value returned by {@code action}
     */
    protected static <T> T withDocument(
            LanguageServerSession<? extends DiagnosticsCapable> session,
            String fileUri,
            String languageId,
            String content,
            long diagnosticsTimeoutSeconds,
            Supplier<T> action) {
        session.languageClient().waitForDiagnostics(fileUri);
        TextDocumentItem item = new TextDocumentItem(fileUri, languageId, 1, content);
        session.textDocumentService().didOpen(new DidOpenTextDocumentParams(item));
        try {
            session.languageClient().awaitDiagnostics(fileUri, diagnosticsTimeoutSeconds, TimeUnit.SECONDS);
            return action.get();
        } finally {
            session.textDocumentService().didClose(
                    new DidCloseTextDocumentParams(new TextDocumentIdentifier(fileUri)));
        }
    }

    /**
     * Tokenises {@code content} with the ANTLR4 lexer, maps each token to an
     * {@link ExtractedToken}, and delegates to {@link #extractSymbols} for optional symbol
     * extraction. Returns a {@link ParsedFile} with an empty reference list
     * (ANTLR4 indexers do not resolve cross-file references).
     */
    @Override
    public ParsedFile indexFile(FileIdentifier fileId, FilePath path,
                                               String content, Object context) {
        try {
            CharStream input = CharStreams.fromString(content);
            Lexer lexer = createLexer(input);
            lexer.removeErrorListeners();

            var allAntlrTokens = new ArrayList<Token>(lexer.getAllTokens());
            var extractedTokens = allAntlrTokens.stream()
                    .map(this::toExtractedToken)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();
            var symbols = extractSymbols(allAntlrTokens, path, fileId);

            return new ParsedFile(
                    /* declarations */ symbols,
                    /* references */ List.of(),
                    /* tokens */ extractedTokens,
                    /* tokenDetails */ List.of(),
                    /* hierarchyEntries */ List.of(),
                    /* highlightGroups */ java.util.Map.of());
        } catch (Exception e) {
            logger.warn("Could not tokenise {}: {}", path.value(), e.getMessage());
            return ParsedFile.empty();
        }
    }

    private Optional<ExtractedToken> toExtractedToken(Token token) {
        if (token.getType() == Token.EOF) {
            return Optional.empty();
        }
        String text = token.getText();
        if (text == null || text.isEmpty()) {
            return Optional.empty();
        }

        int colStart = token.getCharPositionInLine() + 1; // ANTLR4 is 0-based; domain is 1-based
        int colEnd   = token.getCharPositionInLine() + text.length();
        return Optional.of(new ExtractedToken(
                token.getLine(),
                colStart,
                colEnd,
                text,
                mapTokenKind(token.getType()),
                null,
                null,
                false));
    }
}
