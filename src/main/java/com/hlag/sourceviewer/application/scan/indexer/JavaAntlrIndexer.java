package com.hlag.sourceviewer.application.scan.indexer;

import static com.hlag.sourceviewer.domain.model.source.ExtractedToken.TokenKind.BLOCK_COMMENT;
import static com.hlag.sourceviewer.domain.model.source.ExtractedToken.TokenKind.CHAR_LITERAL;
import static com.hlag.sourceviewer.domain.model.source.ExtractedToken.TokenKind.FLOAT_LITERAL;
import static com.hlag.sourceviewer.domain.model.source.ExtractedToken.TokenKind.IDENTIFIER;
import static com.hlag.sourceviewer.domain.model.source.ExtractedToken.TokenKind.INTEGER_LITERAL;
import static com.hlag.sourceviewer.domain.model.source.ExtractedToken.TokenKind.JAVADOC_COMMENT;
import static com.hlag.sourceviewer.domain.model.source.ExtractedToken.TokenKind.KEYWORD;
import static com.hlag.sourceviewer.domain.model.source.ExtractedToken.TokenKind.LINE_COMMENT;
import static com.hlag.sourceviewer.domain.model.source.ExtractedToken.TokenKind.LONG_LITERAL;
import static com.hlag.sourceviewer.domain.model.source.ExtractedToken.TokenKind.OPERATOR;
import static com.hlag.sourceviewer.domain.model.source.ExtractedToken.TokenKind.OTHER;
import static com.hlag.sourceviewer.domain.model.source.ExtractedToken.TokenKind.SEPARATOR;
import static com.hlag.sourceviewer.domain.model.source.ExtractedToken.TokenKind.STRING_LITERAL;
import static com.hlag.sourceviewer.domain.model.source.ExtractedToken.TokenKind.WHITESPACE;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;

import com.hlag.sourceviewer.application.scan.ParsedFile;
import com.hlag.sourceviewer.application.scan.PendingReference;
import com.hlag.sourceviewer.application.scan.antlr.JavaLexer;
import com.hlag.sourceviewer.application.scan.lsp.DiagnosticsCapable;
import com.hlag.sourceviewer.application.scan.lsp.LanguageServerSession;
import com.hlag.sourceviewer.application.scan.lsp.LspManager;
import com.hlag.sourceviewer.application.scan.lsp.LspProjectContext;
import com.hlag.sourceviewer.domain.model.identifier.ColumnNumber;
import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import com.hlag.sourceviewer.domain.model.identifier.LineNumber;
import com.hlag.sourceviewer.domain.model.identifier.QualifiedName;
import com.hlag.sourceviewer.domain.model.identifier.ReferenceKind;
import com.hlag.sourceviewer.domain.model.identifier.SimpleName;
import com.hlag.sourceviewer.domain.model.identifier.SymbolKind;
import com.hlag.sourceviewer.domain.model.repository.Repository;
import com.hlag.sourceviewer.domain.model.source.ExtractedToken;
import com.hlag.sourceviewer.domain.model.source.ExtractedToken.TokenKind;
import com.hlag.sourceviewer.domain.model.source.Symbol;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.MarkedString;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Java fallback indexer using ANTLR tokenization and lightweight symbol extraction.
 *
 * <p>Priority is intentionally between Maven-aware Java indexing and the generic
 * JavaParser fallback.</p>
 *
 * <p>When JDTLS is configured, a language-server session is started during {@link #prepare}
 * and used in {@link #indexFile} to query per-token hover details (type signatures, method
 * signatures) from the language server. The results are stored in the token stream and
 * surfaced in the UI.</p>
 */
@ApplicationScoped
public class JavaAntlrIndexer extends AbstractAntlr4Indexer {

    private static final Logger logger = LoggerFactory.getLogger(JavaAntlrIndexer.class);

    private static final int CLASS_BODY_UNKNOWN = -1;
    private static final int HOVER_REQUEST_TIMEOUT_SECONDS = 5;
    private static final int DOCUMENT_SYMBOL_TIMEOUT_SECONDS = 15;
    private static final int DEFINITION_REQUEST_TIMEOUT_SECONDS = 5;

    private static final Pattern PACKAGE_PATTERN =
            Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);

    private static final Set<String> TYPE_KEYWORDS = Set.of("class", "interface", "enum", "record");
    private static final Set<String> MEMBER_MODIFIERS = Set.of(
            "public", "private", "protected", "static", "final", "abstract", "native", "synchronized",
            "strictfp", "default", "transient", "volatile");

    private static final List<String> BUILD_DESCRIPTORS = List.of("pom.xml", "build.gradle", "build.gradle.kts",
            "settings.gradle", "settings.gradle.kts");

    private static boolean hasBuildDescriptor(Path dir) {
        return BUILD_DESCRIPTORS.stream()
                .map(dir::resolve)
                .anyMatch(Files::isRegularFile);
    }

    private static boolean isBuildDescriptor(Path file) {
        return BUILD_DESCRIPTORS.contains(file.getFileName().toString());
    }

    private final LspManager lspManager;

    @Inject
    public JavaAntlrIndexer(LspManager lspManager) {
        this.lspManager = lspManager;
    }

    /** No-arg constructor for CDI proxy and unit-test construction without LSP. */
    JavaAntlrIndexer() {
        this.lspManager = null;
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
    public boolean handles(FilePath path) {
        return path.hasExtension("java");
    }

    @Override
    protected Lexer createLexer(CharStream input) {
        return new JavaLexer(input);
    }

    /**
     * Starts a JDTLS session for the repository if the language server is available and
     * configured. Returns a {@link JavaAntlrIndexingContext} holding the session (if started)
     * and the repository root path.
     */
    @Override
    public JavaAntlrIndexingContext prepare(Path repoRoot, Repository repository) {
        Path lspProjectRoot = resolveLspProjectRoot(repoRoot);
        if (!lspProjectRoot.equals(repoRoot)) {
            logger.info("Using nested Java project root for JDTLS: '{}' (scan root remains '{}')",
                    lspProjectRoot, repoRoot);
        }
        Optional<LanguageServerSession<? extends DiagnosticsCapable>> session = tryStartLspSession(lspProjectRoot, repository);
        return new JavaAntlrIndexingContext(repoRoot, session);
    }

    private static Path resolveLspProjectRoot(Path repoRoot) {
        if (hasBuildDescriptor(repoRoot)) {
            return repoRoot;
        }

        try (Stream<Path> stream = Files.walk(repoRoot, 3)) {
            List<Path> candidateRoots = stream
                    .filter(Files::isRegularFile)
                    .filter(JavaAntlrIndexer::isBuildDescriptor)
                    .map(Path::getParent)
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted(Comparator.comparingInt(Path::getNameCount))
                    .toList();

            if (candidateRoots.size() == 1) {
                return candidateRoots.getFirst();
            }
        } catch (IOException exception) {
            logger.warn("Failed to resolve lsp project root", exception);
        }

        return repoRoot;
    }

    private Optional<LanguageServerSession<? extends DiagnosticsCapable>> tryStartLspSession(Path repoRoot, Repository repository) {
        if (lspManager == null) {
            return Optional.empty();
        }
        try {
            LanguageServerSession<? extends DiagnosticsCapable> session = lspManager.getLspForLanguage(
                    "java", new LspProjectContext(repository, repoRoot));
            logger.info("JDTLS session started for repository '{}'", repository.name().value());
            return Optional.of(session);
        } catch (Exception e) {
            logger.warn("Could not start JDTLS for repository '{}'.", repository.name().value(), e);
            return Optional.empty();
        }
    }

    /**
     * Indexes the file via ANTLR tokenisation plus (when JDTLS is available) LSP-based
     * symbol extraction, reference resolution, and hover enrichment.
     *
     * <p>All three LSP queries ({@code documentSymbol}, {@code definition}, {@code hover})
     * share one {@code didOpen}/{@code didClose} window to avoid repeated round-trips.</p>
     */
    @Override
    public ParsedFile indexFile(FileIdentifier fileId, FilePath path, String content, Object context) {
        // Phase 1: ANTLR tokenization + ANTLR-based symbol/import extraction (always)
        final var initialParsedFile = super.indexFile(fileId, path, content, context);
        // Phase 1b: assign import group IDs in the token stream
        final var groupedParsedFile = initialParsedFile.withTokens(assignImportGroups(initialParsedFile.tokens()));

        if (!(context instanceof JavaAntlrIndexingContext ctx) || ctx.session().isEmpty()) {
            return groupedParsedFile;
        }

        final var session = ctx.session().get();
        final var fileUri = resolveFileUri(session.projectRoot(), path);
        return withDocument(session, fileUri, "java", content, 10, () ->
                extractInformationFromDocument(session, ctx, groupedParsedFile, fileId, fileUri, content));
    }

    private ParsedFile extractInformationFromDocument(
            final LanguageServerSession<? extends DiagnosticsCapable> session,
            final JavaAntlrIndexingContext indexingContext,
            final ParsedFile parsedFile,
            final FileIdentifier fileId,
            final String fileUri,
            final String content
    ) {
        final var filename = Path.of(URI.create(fileUri)).getFileName().toString();

        // Phase 2a: LSP-based symbol extraction (replaces ANTLR symbols if non-empty)
        final var packagePrefix = extractPackagePrefix(content);
        List<Symbol> lspSymbols = extractSymbolsViaDocumentSymbol(fileId, fileUri, packagePrefix, session);
        List<Symbol> symbols = lspSymbols.isEmpty() ? parsedFile.declarations() : lspSymbols;
        logger.debug("[JDTLS] {}: {} symbols via documentSymbol (fallback={})",
                filename, symbols.size(), lspSymbols.isEmpty());

        // Phase 2b: LSP-based reference resolution via textDocument/definition
        Set<String> declarationPositions = symbols.stream()
                .map(Symbol::toStartLocation)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
        List<PendingReference> references = resolveReferencesViaDefinition(
                fileUri, parsedFile.tokens(), declarationPositions, session, indexingContext.repoRoot());
        logger.debug("[JDTLS] {}: {} references resolved via definition", filename, references.size());

        // Phase 2c: hover enrichment (existing)
        //List<ExtractedToken> enrichedTokens = enrichTokensWithHover(fileUri, parsedFile.tokens(), session, filename);
        var enrichedTokens = parsedFile.tokens();

        return new ParsedFile(symbols, references, enrichedTokens);
    }

    /** Closes the JDTLS session held by the context (if any). */
    @Override
    public void teardown(Object context) {
        if (context instanceof JavaAntlrIndexingContext ctx) {
            ctx.close();
            logger.debug("JDTLS session closed after indexing");
        }
    }

    // -- LSP symbol extraction (documentSymbol) --------------------------------

    private List<Symbol> extractSymbolsViaDocumentSymbol(FileIdentifier fileId, String fileUri,
                                                         String proposedPackagePrefix,
                                                         LanguageServerSession<?> session) {
        try {
            DocumentSymbolParams params = new DocumentSymbolParams(new TextDocumentIdentifier(fileUri));
            List<Either<SymbolInformation, DocumentSymbol>> result = session.textDocumentService()
                    .documentSymbol(params)
                    .get(DOCUMENT_SYMBOL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (result == null || result.isEmpty()) {
                return List.of();
            }
            List<Symbol> symbols = new ArrayList<>();
            // TODO: _IF_ we receive "left", there is more action needed. But with all capabilities enabled, JDTLS should always answer with "right"
            final var allRight = result.stream().allMatch(Either::isRight);
            if (!allRight) {
                // TODO: need better handling if not all eithers are "right"
                for (Either<SymbolInformation, DocumentSymbol> either : result) {
                    if (either.isRight()) {
                        flattenDocumentSymbol(fileId, either.getRight(), proposedPackagePrefix, symbols);
                    } else {
                        logger.warn("Received 'left' for DocumentSymbolParams.");
                        symbolFromSymbolInformation(fileId, either.getLeft(), proposedPackagePrefix).ifPresent(symbols::add);
                    }
                }
            } else {
                final var packagePrefix = result.stream()
                        .map(Either::getRight)
                        .filter(ds -> ds.getKind() == org.eclipse.lsp4j.SymbolKind.Package)
                        .findFirst()
                        .map(DocumentSymbol::getName)
                        .orElse("");
                result.stream()
                        .map(Either::getRight)
                        .filter(ds -> ds.getKind() != org.eclipse.lsp4j.SymbolKind.Package)
                        .forEach(documentSymbol ->
                                flattenDocumentSymbol(fileId, documentSymbol, packagePrefix, symbols));
            }
            return symbols;
        } catch (Exception e) {
            logger.debug("[JDTLS] documentSymbol failed for {}: {}", fileUri, e.getMessage());
            return List.of();
        }
    }

    private void flattenDocumentSymbol(FileIdentifier fileId, DocumentSymbol documentSymbol,
                                       String parentFullQualifiedName, List<Symbol> out) {
        org.eclipse.lsp4j.SymbolKind lspKind = documentSymbol.getKind();
        SymbolKind kind = mapLspSymbolKind(lspKind);
        String languageKind = lspKind != null ? lspKind.name().toLowerCase(Locale.ROOT) : null;

        String fullQualifiedName = parentFullQualifiedName.isEmpty()
                ? documentSymbol.getName()
                : parentFullQualifiedName + "." + documentSymbol.getName();

        var selRange  = documentSymbol.getSelectionRange();
        var fullRange = documentSymbol.getRange();
        int lineStart  = selRange  != null ? selRange.getStart().getLine() + 1              : 0;
        int colStart   = selRange  != null ? selRange.getStart().getCharacter() + 1         : 0;
        int colEnd     = selRange  != null ? selRange.getEnd().getCharacter()               : 0;
        int lineEnd    = fullRange != null ? fullRange.getEnd().getLine() + 1               : 0;

        Symbol s = new Symbol(
                fileId, kind,
                new SimpleName(documentSymbol.getName()),
                new QualifiedName(fullQualifiedName),
                ofNullable(documentSymbol.getDetail()).filter(d -> !d.isBlank()).map(SimpleName::new),
                lineStart > 0  ? Optional.of(new LineNumber(lineStart))   : Optional.empty(),
                lineEnd   > 0  ? Optional.of(new LineNumber(lineEnd))     : Optional.empty(),
                colStart  > 0  ? Optional.of(new ColumnNumber(colStart))  : Optional.empty(),
                colEnd    > 0  ? Optional.of(new ColumnNumber(colEnd))    : Optional.empty(),
                ofNullable(languageKind),
                List.of());
        out.add(s);

        if (documentSymbol.getChildren() != null) {
            for (DocumentSymbol child : documentSymbol.getChildren()) {
                flattenDocumentSymbol(fileId, child, fullQualifiedName, out);
            }
        }
    }

    private Optional<Symbol> symbolFromSymbolInformation(FileIdentifier fileId,
                                                          SymbolInformation info,
                                                          String packagePrefix) {
        if (info == null || info.getName() == null) {
            return empty();
        }
        // TODO: this fqn might be wrong
        final var fullQualifiedName = new QualifiedName(packagePrefix + getContainer(info) + info.getName());
        final var kind = mapLspSymbolKind(info.getKind());
        final var languageKind = info.getKind() != null ? info.getKind().name().toLowerCase(Locale.ROOT) : null;
        final var loc = info.getLocation();
        final var startLine = loc != null ? loc.getRange().getStart().getLine() + 1 : 0;
        final var startCol  = loc != null ? loc.getRange().getStart().getCharacter() + 1 : 0;
        final var endLine = loc != null ? loc.getRange().getEnd().getLine() + 1 : 0;
        final var endCol  = loc != null ? loc.getRange().getEnd().getCharacter() + 1 : 0;
        return of(new Symbol(
                fileId,
                kind,
                new SimpleName(info.getName()),
                fullQualifiedName,
                empty(),
                startLine > 0 ? of(new LineNumber(startLine)) : empty(),
                endLine > 0 ? of(new LineNumber(endLine)) : empty(),
                startCol  > 0 ? of(new ColumnNumber(startCol)) : empty(),
                endCol > 0 ? of(new ColumnNumber(endCol)) : empty(),
                ofNullable(languageKind),
                List.of()));
    }

    private String getContainer(SymbolInformation info) {
        if (info == null) {
            return "";
        }
        if (info.getContainerName() == null || info.getContainerName().isBlank()) {
            return "";
        }
        return info.getContainerName() + ".";
    }

    private static SymbolKind mapLspSymbolKind(org.eclipse.lsp4j.SymbolKind lspKind) {
        if (lspKind == null) return SymbolKind.CLASS;
        return switch (lspKind) {
            case Class, Struct -> SymbolKind.CLASS;
            case Interface -> SymbolKind.INTERFACE;
            case Enum     -> SymbolKind.ENUM;
            case EnumMember -> SymbolKind.ENUM_CONSTANT;
            case Method   -> SymbolKind.METHOD;
            case Constructor -> SymbolKind.CONSTRUCTOR;
            case Field    -> SymbolKind.FIELD;
            case Property -> SymbolKind.PROPERTY;
            case Variable -> SymbolKind.LOCAL_VARIABLE;
            case Function -> SymbolKind.FUNCTION;
            case File -> null;
            case Module   -> SymbolKind.MODULE;
            case Namespace -> SymbolKind.NAMESPACE;
            case TypeParameter -> SymbolKind.TYPE_ALIAS;
            case Constant ->  SymbolKind.CONSTANT;
            default       -> {
                logger.warn("Unknown symbol kind: {}", lspKind);
                yield SymbolKind.VARIABLE;
            } // TODO: Map ALL lspKinds here
        };
    }

    // -- LSP reference resolution (definition) ---------------------------------

    private List<PendingReference> resolveReferencesViaDefinition(
            String fileUri,
            List<ExtractedToken> tokens,
            Set<String> declarationPositions,
            LanguageServerSession<?> session,
            Path repoRoot) {
        List<PendingReference> references = new ArrayList<>();
        for (ExtractedToken token : tokens) {
            if (token.kind() != IDENTIFIER) {
                continue;
            }
            String posKey = token.line() + ":" + token.columnStart();
            if (declarationPositions.contains(posKey)) {
                continue; // this is a declaration, not a reference
            }
            Position position = new Position(token.line() - 1, Math.max(0, token.columnStart() - 1));
            DefinitionParams params = new DefinitionParams(new TextDocumentIdentifier(fileUri), position);
            try {
                // definition() returns Either<List<Location>, List<LocationLink>>
                var result = session.textDocumentService()
                        .definition(params)
                        .get(DEFINITION_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (result == null) continue;
                Location loc = extractFirstLocation(result);
                if (loc == null) continue;
                // Only create a reference if the definition is within the repository
                Optional<FilePath> defPath = uriToRepoRelativePath(loc.getUri(), repoRoot);
                if (defPath.isEmpty()) continue;
                int defLine = loc.getRange().getStart().getLine() + 1;
                int defCol  = loc.getRange().getStart().getCharacter() + 1;
                references.add(new PendingReference(
                        Optional.empty(),
                        Optional.of(new SimpleName(token.text())),
                        ReferenceKind.TYPE_USE,
                        Optional.of(new LineNumber(token.line())),
                        Optional.of(new ColumnNumber(token.columnStart())),
                        defPath,
                        Optional.of(new LineNumber(defLine)),
                        Optional.of(new ColumnNumber(defCol))));
            } catch (Exception e) {
                logger.trace("[JDTLS] definition failed for token '{}' at {}:{}: {}",
                        token.text(), token.line(), token.columnStart(), e.getMessage());
            }
        }
        return references;
    }

    private static Location extractFirstLocation(Object result) {
        if (result instanceof Either<?,?> either) {
            if (either.isLeft()) {
                Object left = either.getLeft();
                if (left instanceof List<?> list && !list.isEmpty() && list.getFirst() instanceof Location loc) {
                    return loc;
                }
            } else {
                Object right = either.getRight();
                if (right instanceof List<?> list && !list.isEmpty()) {
                    Object first = list.getFirst();
                    if (first instanceof Location loc) return loc;
                    if (first instanceof LocationLink link) {
                        Location l = new Location();
                        l.setUri(link.getTargetUri());
                        l.setRange(link.getTargetSelectionRange());
                        return l;
                    }
                }
            }
        }
        if (result instanceof List<?> list && !list.isEmpty() && list.getFirst() instanceof Location loc) {
            return loc;
        }
        return null;
    }

    private static Optional<FilePath> uriToRepoRelativePath(String uri, Path repoRoot) {
        if (uri == null) return Optional.empty();
        try {
            Path target = Path.of(URI.create(uri)).toAbsolutePath();
            Path root   = repoRoot.toAbsolutePath().toRealPath();
            if (!target.startsWith(root)) return Optional.empty();
            return Optional.of(new FilePath(root.relativize(target).toString()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // -- LSP hover enrichment --------------------------------------------------

    private List<ExtractedToken> enrichTokensWithHover(String fileUri, List<ExtractedToken> tokens,
                                                        LanguageServerSession<?> session,
                                                        String filename) {
        int queried = 0, withContent = 0, emptyOrNull = 0, failed = 0;
        List<ExtractedToken> enriched = new ArrayList<>(tokens.size());
        for (ExtractedToken token : tokens) {
            if (token.kind() != IDENTIFIER) {
                enriched.add(token);
                continue;
            }
            queried++;
            // LSP positions are 0-based; ANTLR lines are 1-based, columns are 0-based
            Position position = new Position(token.line() - 1, Math.max(0, token.columnStart() - 1));
            HoverParams params = new HoverParams(new TextDocumentIdentifier(fileUri), position);
            try {
                Hover hover = session.textDocumentService().hover(params)
                        .get(HOVER_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                String hoverText = hover != null && hover.getContents() != null
                        ? formatHoverContents(hover) : null;
                if (hoverText != null && !hoverText.isBlank()) {
                    withContent++;
                    enriched.add(new ExtractedToken(token.line(), token.columnStart(), token.columnEnd(),
                            token.text(), token.kind(), token.qualifiedName(), token.symbolId(),
                            hoverText, token.groupId()));
                } else {
                    emptyOrNull++;
                    enriched.add(token);
                }
            } catch (Exception e) {
                failed++;
                logger.trace("[JDTLS] hover failed for token '{}' at {}:{}: {}",
                        token.text(), token.line(), token.columnStart(), e.getMessage());
                enriched.add(token);
            }
        }
        logger.debug("[JDTLS] {}: {} identifiers queried, {} with hover, {} empty/null, {} failed",
                filename, queried, withContent, emptyOrNull, failed);
        return enriched;
    }

    // -- Import group assignment -----------------------------------------------

    /**
     * Walks the token list and assigns a shared {@code groupId} to all tokens that
     * belong to the same {@code import} statement. All tokens in the group also receive
     * the full import FQN as their {@code qualifiedName} so the frontend can treat them
     * as a single navigable link.
     */
    static List<ExtractedToken> assignImportGroups(List<ExtractedToken> tokens) {
        List<ExtractedToken> result = new ArrayList<>(tokens.size());
        int nextGroupId = 1;
        int currentIndex = 0;
        while (currentIndex < tokens.size()) {
            final var token = tokens.get(currentIndex);
            result.add(token);
            ++currentIndex;
            // Detect: KEYWORD "import" followed by identifiers and dots until ";"
            if (token.is(KEYWORD, "import")) {
                // Collect all tokens from the KEYWORD through the terminating SEPARATOR(";")
                int groupId = nextGroupId++;
                int indexWithinImport = currentIndex;
                // Skip optional whitespace and "static" keyword for static imports
                while (indexWithinImport < tokens.size() && tokens.get(indexWithinImport).is(WHITESPACE)) {
                    result.add(tokens.get(indexWithinImport));
                    ++indexWithinImport;
                }
                if (indexWithinImport < tokens.size() && tokens.get(indexWithinImport).is(KEYWORD, "static")) {
                    result.add(tokens.get(indexWithinImport));
                    ++indexWithinImport; // skip "static"
                }
                while (indexWithinImport < tokens.size() && tokens.get(indexWithinImport).is(WHITESPACE)) {
                    result.add(tokens.get(indexWithinImport));
                    ++indexWithinImport;
                }
                final var fqnBuilder = new StringBuilder();
                final var startIndex = indexWithinImport;
                var endIndex = indexWithinImport; // exclusive end of import group (after ";")
                while (indexWithinImport < tokens.size()) {
                    final var importToken = tokens.get(indexWithinImport);
                    if (importToken.is(WHITESPACE)) { ++indexWithinImport; continue; } // skip whitespace
                    if (importToken.is(IDENTIFIER)) {
                        fqnBuilder.append(importToken.text());
                        ++indexWithinImport;
                    } else if (importToken.is(SEPARATOR, ".")) {
                        fqnBuilder.append('.');
                        ++indexWithinImport;
                    } else if (importToken.is(SEPARATOR, ";")) {
                        endIndex = indexWithinImport + 1;
                        break;
                    } else if (importToken.is(OPERATOR, "*")) {
                        fqnBuilder.append('*');
                        ++indexWithinImport;
                    } else {
                        endIndex = indexWithinImport;
                        break;
                    }
                }
                final var fqn = fqnBuilder.toString();
                // Emit tokens from startIndex to endIndex with groupId+fqn
                tokens.subList(startIndex, endIndex).forEach(importToken ->
                    result.add(importToken.withQualifiedName(fqn).withGroupId(groupId))
                );
                currentIndex = endIndex;
            }
        }
        return result;
    }

    private static String extractPackagePrefix(String content) {
        Matcher m = PACKAGE_PATTERN.matcher(content);
        return m.find() ? m.group(1) + "." : "";
    }

    private static String resolveFileUri(Path repoRoot, FilePath path) {
        Path resolved = repoRoot.resolve(Path.of(path.value()));
        try {
            // toRealPath() resolves symlinks so the URI matches what JDTLS sends back
            // (e.g. on macOS /var is a symlink to /private/var)
            return resolved.toRealPath().toUri().toString();
        } catch (IOException e) {
            return resolved.toAbsolutePath().toUri().toString();
        }
    }

    private static String formatHoverContents(Hover hover) {
        List<String> parts = new ArrayList<>();
        collectHoverParts(hover.getContents(), parts);
        return String.join(" | ", parts).strip();
    }

    private static void collectHoverParts(Object value, List<String> parts) {
        if (value == null) {
            return;
        }
        if (value instanceof String text) {
            addPart(parts, text);
            return;
        }
        if (value instanceof MarkupContent markupContent) {
            addPart(parts, markupContent.getValue());
            return;
        }
        if (value instanceof MarkedString markedString) {
            addPart(parts, markedString.getValue());
            return;
        }
        if (value instanceof List<?> list) {
            for (Object item : list) {
                collectHoverParts(item, parts);
            }
            return;
        }

        Object eitherLikeValue = extractEitherLikeValue(value);
        if (eitherLikeValue != null) {
            collectHoverParts(eitherLikeValue, parts);
            return;
        }

        String reflectedValue = extractValueProperty(value);
        if (reflectedValue != null) {
            addPart(parts, reflectedValue);
        }
    }

    private static void addPart(List<String> parts, String value) {
        if (value != null) {
            String stripped = value.strip();
            if (!stripped.isEmpty()) {
                parts.add(stripped);
            }
        }
    }

    private static Object extractEitherLikeValue(Object value) {
        Object extracted = invokeChosenBranch(value, "isLeft", "getLeft");
        if (extracted != null) {
            return extracted;
        }
        extracted = invokeChosenBranch(value, "isMiddle", "getMiddle");
        if (extracted != null) {
            return extracted;
        }
        return invokeChosenBranch(value, "isRight", "getRight");
    }

    private static Object invokeChosenBranch(Object target, String chooseMethod, String valueMethod) {
        try {
            Method chooser = target.getClass().getMethod(chooseMethod);
            Object chosen = chooser.invoke(target);
            if (chosen instanceof Boolean bool && bool) {
                Method accessor = target.getClass().getMethod(valueMethod);
                return accessor.invoke(target);
            }
        } catch (ReflectiveOperationException ignored) {
            // Not an Either-like object for this branch.
        }
        return null;
    }

    private static String extractValueProperty(Object value) {
        try {
            Method method = value.getClass().getMethod("getValue");
            Object result = method.invoke(value);
            return result == null ? null : result.toString();
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    // -- ANTLR tokenization ----------------------------------------------------

    @Override
    protected TokenKind mapTokenKind(int tokenType) {
        return switch (tokenType) {
            case JavaLexer.JavadocComment                 -> JAVADOC_COMMENT;
            case JavaLexer.BlockComment                   -> BLOCK_COMMENT;
            case JavaLexer.LineComment                    -> LINE_COMMENT;
            case JavaLexer.TextBlock, JavaLexer.StringDouble -> STRING_LITERAL;
            case JavaLexer.CharLiteral                    -> CHAR_LITERAL;
            case JavaLexer.FloatLiteral                   -> FLOAT_LITERAL;
            case JavaLexer.LongLiteral                    -> LONG_LITERAL;
            case JavaLexer.HexLiteral,
                 JavaLexer.BinaryLiteral,
                 JavaLexer.IntegerLiteral                 -> INTEGER_LITERAL;
            case JavaLexer.Keyword                        -> KEYWORD;
            case JavaLexer.Identifier                     -> IDENTIFIER;
            case JavaLexer.Operator                       -> OPERATOR;
            case JavaLexer.Separator                      -> SEPARATOR;
            case JavaLexer.Whitespace                     -> WHITESPACE;
            default                                       -> OTHER;
        };
    }

    @Override
    protected List<Symbol> extractSymbols(List<Token> tokens, FilePath path, FileIdentifier fileId) {
        List<Token> structural = tokens.stream()
                .filter(t -> t.getType() != JavaLexer.Whitespace
                        && t.getType() != JavaLexer.LineComment
                        && t.getType() != JavaLexer.BlockComment
                        && t.getType() != JavaLexer.JavadocComment
                        && t.getType() != Token.EOF
                        && t.getType() != JavaLexer.Other)
                .toList();

        List<Symbol> symbols = new ArrayList<>();
        List<TypeContext> typeStack = new ArrayList<>();
        String packagePrefix = readPackagePrefix(structural);
        int braceDepth = 0;

        for (int i = 0; i < structural.size(); i++) {
            Token tok = structural.get(i);
            String text = tok.getText();

            if (isSeparator(tok, "{")) {
                braceDepth++;
                if (!typeStack.isEmpty() && typeStack.getLast().bodyDepth == CLASS_BODY_UNKNOWN) {
                    typeStack.getLast().bodyDepth = braceDepth;
                }
                continue;
            }

            if (isSeparator(tok, "}")) {
                if (!typeStack.isEmpty() && braceDepth == typeStack.getLast().bodyDepth) {
                    typeStack.removeLast();
                }
                braceDepth--;
                continue;
            }

            if (tok.getType() == JavaLexer.Keyword && TYPE_KEYWORDS.contains(text)) {
                Token nameTok = lookAhead(structural, i + 1);
                if (nameTok != null && nameTok.getType() == JavaLexer.Identifier) {
                    String typeName = nameTok.getText();
                    SymbolKind kind = mapTypeKind(text);
                    String qualified = buildQualifiedTypeName(packagePrefix, typeStack, typeName);
                    symbols.add(buildSymbol(fileId, kind, nameTok, qualified, Optional.empty(), List.of()));
                    typeStack.add(new TypeContext(typeName, CLASS_BODY_UNKNOWN));
                    i++;
                }
                continue;
            }

            if (tok.getType() == JavaLexer.Separator && "@".equals(text)) {
                i = skipAnnotation(structural, i);
                continue;
            }

            if (!typeStack.isEmpty() && braceDepth == typeStack.getLast().bodyDepth) {
                int memberStart = i;
                while (memberStart < structural.size() && isMemberModifier(structural.get(memberStart))) {
                    memberStart++;
                }
                if (memberStart >= structural.size()) {
                    continue;
                }

                Token returnOrName = structural.get(memberStart);
                Token maybeName = lookAhead(structural, memberStart + 1);
                int nameIndex;
                Token nameTok;

                if (isIdentifier(returnOrName) && maybeName != null && isSeparator(maybeName, "(")) {
                    nameTok = returnOrName;
                    nameIndex = memberStart;
                } else if (isIdentifier(returnOrName) && isIdentifier(maybeName)) {
                    Token afterName = lookAhead(structural, memberStart + 2);
                    if (afterName != null && isSeparator(afterName, "(")) {
                        nameTok = maybeName;
                        nameIndex = memberStart + 1;
                    } else if (afterName != null
                            && (isSeparator(afterName, ";") || isOperator(afterName, "=")
                                    || isSeparator(afterName, ",") || isSeparator(afterName, ")"))) {
                        // Field declaration: TypeName fieldName (;|=|,|))
                        String fieldFqn = buildQualifiedMemberName(packagePrefix, typeStack, maybeName.getText());
                        List<String> modifiers = readModifiers(structural, i, memberStart);
                        symbols.add(buildSymbol(fileId, SymbolKind.FIELD, maybeName, fieldFqn, Optional.empty(), modifiers));
                        i = memberStart + 1;
                        continue;
                    } else {
                        continue;
                    }
                } else {
                    continue;
                }

                int openParenIndex = nameIndex + 1;
                int closeParenIndex = findClosingParen(structural, openParenIndex);
                if (closeParenIndex == -1) {
                    continue;
                }
                int declContinuation = closeParenIndex + 1;
                while (declContinuation < structural.size()
                        && structural.get(declContinuation).getType() == JavaLexer.Keyword
                        && "throws".equals(structural.get(declContinuation).getText())) {
                    declContinuation = skipTypeList(structural, declContinuation + 1);
                }
                Token tail = lookAhead(structural, declContinuation);
                if (tail == null || !(isSeparator(tail, "{") || isSeparator(tail, ";"))) {
                    continue;
                }

                String currentType = typeStack.getLast().name;
                boolean constructor = currentType.equals(nameTok.getText());
                SymbolKind kind = constructor ? SymbolKind.CONSTRUCTOR : SymbolKind.METHOD;
                String qualifiedName = buildQualifiedMemberName(packagePrefix, typeStack, nameTok.getText());
                String signature = buildSimpleSignature(nameTok.getText(), structural, openParenIndex, closeParenIndex);
                List<String> modifiers = readModifiers(structural, i, memberStart);
                symbols.add(buildSymbol(fileId, kind, nameTok, qualifiedName, Optional.of(signature), modifiers));

                // Extract parameters for this method/constructor
                extractParameters(fileId, structural, openParenIndex, closeParenIndex,
                        qualifiedName, symbols);
                i = closeParenIndex;
            }
        }
        return symbols;
    }

    /**
     * Extracts PARAMETER symbols from the parameter list of a method or constructor.
     *
     * @param methodFqn the FQN of the enclosing method (parameters are named
     *                  {@code methodFqn.paramName})
     */
    private static void extractParameters(FileIdentifier fileId, List<Token> structural,
                                          int openParen, int closeParen,
                                          String methodFqn, List<Symbol> out) {
        // Scan from openParen+1 to closeParen looking for "TypeName paramName" pairs.
        // We skip generics/arrays as best-effort; final/annotation modifiers skipped too.
        int i = openParen + 1;
        while (i < closeParen) {
            // Skip annotations
            if (isSeparator(structural.get(i), "@")) {
                i = skipAnnotation(structural, i) + 1;
                continue;
            }
            // Skip final keyword
            if (structural.get(i).getType() == JavaLexer.Keyword
                    && "final".equals(structural.get(i).getText())) {
                i++;
                continue;
            }
            // Skip generic type bounds, arrays: <, >, [, ]
            String txt = structural.get(i).getText();
            if ("<".equals(txt) || ">".equals(txt) || "[".equals(txt) || "]".equals(txt)) {
                i++;
                continue;
            }
            // Expect TypeName paramName
            if (!isIdentifier(structural.get(i))) { i++; continue; }
            Token typeTok = structural.get(i);
            // Advance past qualified type (e.g. java.util.List)
            int j = i + 1;
            while (j < closeParen && isSeparator(structural.get(j), ".")) {
                j += 2; // skip "." and next identifier
            }
            // Skip vararg "..."
            if (j < closeParen && structural.get(j).getType() == JavaLexer.Operator
                    && "...".equals(structural.get(j).getText())) {
                j++;
            }
            // Skip array brackets
            while (j < closeParen && (isSeparator(structural.get(j), "[") || isSeparator(structural.get(j), "]"))) {
                j++;
            }
            if (j < closeParen && isIdentifier(structural.get(j))) {
                Token paramTok = structural.get(j);
                String paramFqn = methodFqn + "." + paramTok.getText();
                out.add(buildSymbol(fileId, SymbolKind.PARAMETER, paramTok, paramFqn,
                        Optional.empty(), List.of()));
                i = j + 1;
            } else {
                i = j + 1;
            }
            // Skip comma
            if (i < closeParen && isSeparator(structural.get(i), ",")) {
                i++;
            }
        }
    }

    private static Symbol buildSymbol(FileIdentifier fileId, SymbolKind kind, Token tok, String qualifiedName,
                                      Optional<String> signature, List<String> modifiers) {
        int col = tok.getCharPositionInLine() + 1;
        return new Symbol(
                fileId,
                kind,
                new SimpleName(tok.getText()),
                new QualifiedName(qualifiedName),
                signature.map(SimpleName::new),
                Optional.of(new LineNumber(tok.getLine())),
                Optional.empty(),
                Optional.of(new ColumnNumber(col)),
                modifiers);
    }

    private static SymbolKind mapTypeKind(String keyword) {
        return switch (keyword) {
            case "class" -> SymbolKind.CLASS;
            case "interface" -> SymbolKind.INTERFACE;
            case "enum" -> SymbolKind.ENUM;
            case "record" -> SymbolKind.RECORD;
            default -> SymbolKind.CLASS;
        };
    }

    private static String readPackagePrefix(List<Token> structural) {
        for (int i = 0; i < structural.size() - 2; i++) {
            Token tok = structural.get(i);
            if (tok.getType() == JavaLexer.Keyword && "package".equals(tok.getText())) {
                StringBuilder pkg = new StringBuilder();
                for (int j = i + 1; j < structural.size(); j++) {
                    Token part = structural.get(j);
                    if (isSeparator(part, ";")) {
                        return pkg.isEmpty() ? "" : pkg + ".";
                    }
                    if (part.getType() == JavaLexer.Identifier) {
                        if (!pkg.isEmpty()) {
                            pkg.append('.');
                        }
                        pkg.append(part.getText());
                    }
                }
                break;
            }
        }
        return "";
    }

    private static String buildQualifiedTypeName(String packagePrefix, List<TypeContext> typeStack, String typeName) {
        if (typeStack.isEmpty()) {
            return packagePrefix + typeName;
        }
        String nested = typeStack.stream().map(ctx -> ctx.name).reduce((a, b) -> a + "." + b).orElse("");
        return packagePrefix + nested + "." + typeName;
    }

    private static String buildQualifiedMemberName(String packagePrefix, List<TypeContext> typeStack, String memberName) {
        String nested = typeStack.stream().map(ctx -> ctx.name).reduce((a, b) -> a + "." + b).orElse("");
        return packagePrefix + nested + "." + memberName;
    }

    private static int skipAnnotation(List<Token> tokens, int atIndex) {
        int i = atIndex + 1;
        while (i < tokens.size() && (tokens.get(i).getType() == JavaLexer.Identifier || isSeparator(tokens.get(i), "."))) {
            i++;
        }
        if (i < tokens.size() && isSeparator(tokens.get(i), "(")) {
            int close = findClosingParen(tokens, i);
            return close == -1 ? atIndex : close;
        }
        return i - 1;
    }

    private static int findClosingParen(List<Token> tokens, int openParenIndex) {
        if (openParenIndex >= tokens.size() || !isSeparator(tokens.get(openParenIndex), "(")) {
            return -1;
        }
        int depth = 0;
        for (int i = openParenIndex; i < tokens.size(); i++) {
            if (isSeparator(tokens.get(i), "(")) {
                depth++;
            } else if (isSeparator(tokens.get(i), ")")) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int skipTypeList(List<Token> tokens, int startIndex) {
        int i = startIndex;
        while (i < tokens.size()) {
            Token tok = tokens.get(i);
            if (isSeparator(tok, "{") || isSeparator(tok, ";")) {
                return i;
            }
            i++;
        }
        return i;
    }

    private static String buildSimpleSignature(String name, List<Token> tokens, int openParen, int closeParen) {
        StringBuilder sb = new StringBuilder();
        for (int i = openParen + 1; i < closeParen; i++) {
            Token tok = tokens.get(i);
            if (tok.getType() == JavaLexer.Identifier
                    || isSeparator(tok, ",")
                    || isSeparator(tok, "[")
                    || isSeparator(tok, "]")
                    || (tok.getType() == JavaLexer.Operator && "...".equals(tok.getText()))) {
                if (!sb.isEmpty() && shouldInsertSpace(sb, tok)) {
                    sb.append(' ');
                }
                sb.append(tok.getText());
            }
        }
        return name + "(" + sb + ")";
    }

    private static boolean shouldInsertSpace(StringBuilder sb, Token tok) {
        if (sb.isEmpty()) {
            return false;
        }
        char last = sb.charAt(sb.length() - 1);
        if (last == '[' || last == ',' || last == '.') {
            return false;
        }
        String text = tok.getText();
        return !"]".equals(text) && !",".equals(text) && !".".equals(text);
    }

    private static List<String> readModifiers(List<Token> tokens, int from, int toExclusive) {
        List<String> modifiers = new ArrayList<>();
        for (int i = from; i < toExclusive; i++) {
            Token tok = tokens.get(i);
            if (tok.getType() == JavaLexer.Keyword && MEMBER_MODIFIERS.contains(tok.getText())) {
                modifiers.add(tok.getText());
            }
        }
        return modifiers;
    }

    private static boolean isMemberModifier(Token tok) {
        return tok.getType() == JavaLexer.Keyword && MEMBER_MODIFIERS.contains(tok.getText());
    }

    private static boolean isIdentifier(Token tok) {
        return tok != null && tok.getType() == JavaLexer.Identifier;
    }


    private static boolean isSeparator(Token tok, String text) {
        return tok.getType() == JavaLexer.Separator && text.equals(tok.getText());
    }

    private static boolean isOperator(Token tok, String text) {
        return tok.getType() == JavaLexer.Operator && text.equals(tok.getText());
    }

    private static Token lookAhead(List<Token> tokens, int index) {
        return index < tokens.size() ? tokens.get(index) : null;
    }

    private static final class TypeContext {
        private final String name;
        private int bodyDepth;

        private TypeContext(String name, int bodyDepth) {
            this.name = name;
            this.bodyDepth = bodyDepth;
        }
    }
}
