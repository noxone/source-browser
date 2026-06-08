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
import com.hlag.sourceviewer.domain.port.outgoing.JsonSerializer;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private final JsonSerializer jsonMapper;

    @Inject
    public JavaAntlrIndexer(LspManager lspManager, JsonSerializer jsonMapper) {
        this.lspManager = lspManager;
        this.jsonMapper = jsonMapper;
    }

    /** No-arg constructor for CDI proxy and unit-test construction without LSP. */
    JavaAntlrIndexer() {
        this.lspManager = null;
        this.jsonMapper = new com.hlag.sourceviewer.domain.port.outgoing.JsonSerializer() {
            @Override public java.util.Map<String, Object> parseToMap(String json) { return java.util.Map.of(); }
            @Override public String serialize(Object object) { return "{}"; }
        };
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
        return withDocument(session, fileUri, "java", content, 300, () ->
                extractInformationFromDocument(session, ctx, groupedParsedFile, fileId, path, fileUri, content));
    }

    private ParsedFile extractInformationFromDocument(
            final LanguageServerSession<? extends DiagnosticsCapable> session,
            final JavaAntlrIndexingContext indexingContext,
            final ParsedFile parsedFile,
            final FileIdentifier fileId,
            final FilePath filePath,
            final String fileUri,
            final String content
    ) {
        final var filename = Path.of(URI.create(fileUri)).getFileName().toString();
        final var textDocId = new TextDocumentIdentifier(fileUri);

        // Phase 2a: LSP-based symbol extraction (replaces ANTLR symbols if non-empty)
        final var packagePrefix = extractPackagePrefix(content);
        List<Symbol> lspSymbols = extractSymbolsViaDocumentSymbol(fileId, fileUri, packagePrefix, session);
        List<Symbol> symbols = lspSymbols.isEmpty() ? parsedFile.declarations() : lspSymbols;
        logger.debug("[JDTLS] {}: {} symbols via documentSymbol (fallback={})",
                filename, symbols.size(), lspSymbols.isEmpty());

        // Build symbol position lookup for declaration token enrichment
        Map<String, Symbol> symbolsByPosition = new HashMap<>();
        for (Symbol sym : symbols) {
            sym.toStartLocation().ifPresent(key -> symbolsByPosition.put(key, sym));
        }

        // Phase 2b: LSP-based reference resolution via textDocument/definition
        Set<String> declarationPositions = symbolsByPosition.keySet();
        List<PendingReference> references = resolveReferencesViaDefinition(
                fileUri, parsedFile.tokens(), declarationPositions, session, indexingContext.repoRoot());
        logger.debug("[JDTLS] {}: {} references resolved via definition", filename, references.size());

        // Build import map once — shared by detail extraction and hierarchy extraction
        Map<String, String> importMap = buildImportMap(parsedFile.tokens());

        // Build params-by-method map for METHOD_DECL parameter info
        Map<String, List<Symbol>> paramsByMethod = new HashMap<>();
        for (Symbol sym : symbols) {
            if (sym.kind() == com.hlag.sourceviewer.domain.model.identifier.SymbolKind.PARAMETER) {
                String fqn = sym.qualifiedName().value();
                int lastDot = fqn.lastIndexOf('.');
                if (lastDot > 0) {
                    paramsByMethod.computeIfAbsent(fqn.substring(0, lastDot), k -> new ArrayList<>()).add(sym);
                }
            }
        }

        // Phase 2c: Token detail extraction via LSP hover + import map fallback
        List<com.hlag.sourceviewer.domain.model.source.TokenDetail> tokenDetails =
                extractTokenDetails(session, textDocId, fileId, parsedFile.tokens(),
                        declarationPositions, symbolsByPosition, importMap, packagePrefix,
                        paramsByMethod, filename);
        // Phase 2c2: import-identifier details (always clickable without hover)
        tokenDetails.addAll(extractImportTokenDetails(parsedFile.tokens(), fileId, importMap));
        logger.debug("[JDTLS] {}: {} token details extracted", filename, tokenDetails.size());

        // Phase 2d: Highlight groups from definition locations
        Map<String, Integer> highlightGroups =
                computeHighlightGroups(references, filePath, symbolsByPosition);
        logger.debug("[JDTLS] {}: {} highlight groups computed", filename, highlightGroups.size());

        // Phase 2e: Type hierarchy from ANTLR token stream
        List<com.hlag.sourceviewer.domain.model.source.TypeHierarchyEntry> hierarchy =
                extractTypeHierarchy(parsedFile.tokens(), packagePrefix, importMap);
        logger.debug("[JDTLS] {}: {} type hierarchy entries extracted", filename, hierarchy.size());

        return new ParsedFile(symbols, references, parsedFile.tokens(),
                tokenDetails, hierarchy, highlightGroups);
    }

    // -- Token detail extraction (hover) ---------------------------------------

    private List<com.hlag.sourceviewer.domain.model.source.TokenDetail> extractTokenDetails(
            LanguageServerSession<?> session,
            TextDocumentIdentifier textDocId,
            FileIdentifier fileId,
            List<ExtractedToken> tokens,
            Set<String> declarationPositions,
            Map<String, Symbol> symbolsByPosition,
            Map<String, String> importMap,
            String packagePrefix,
            Map<String, List<Symbol>> paramsByMethod,
            String filename) {

        // Detect annotation token positions: IDENTIFIER immediately preceded by '@'
        Set<String> annotationPositions = new java.util.HashSet<>();
        for (int i = 1; i < tokens.size(); i++) {
            ExtractedToken prev = tokens.get(i - 1);
            ExtractedToken cur  = tokens.get(i);
            if (cur.is(IDENTIFIER) && prev.is(SEPARATOR, "@")) {
                annotationPositions.add(cur.line() + ":" + cur.columnStart());
            } else if (cur.is(IDENTIFIER) && prev.is(WHITESPACE)) {
                // Look further back through whitespace
                for (int j = i - 2; j >= 0; j--) {
                    ExtractedToken t = tokens.get(j);
                    if (t.is(WHITESPACE)) continue;
                    if (t.is(SEPARATOR, "@")) {
                        annotationPositions.add(cur.line() + ":" + cur.columnStart());
                    }
                    break;
                }
            }
        }

        List<com.hlag.sourceviewer.domain.model.source.TokenDetail> result = new ArrayList<>();
        for (int tokenIdx = 0; tokenIdx < tokens.size(); tokenIdx++) {
            ExtractedToken token = tokens.get(tokenIdx);
            if (!token.is(IDENTIFIER)) {
                continue;
            }
            String posKey = token.line() + ":" + token.columnStart();

            // For declaration tokens, build detail from symbol info (no LSP call needed)
            if (declarationPositions.contains(posKey)) {
                Symbol sym = symbolsByPosition.get(posKey);
                if (sym != null) {
                    com.hlag.sourceviewer.domain.model.source.TokenDetail td =
                            buildDetailFromSymbol(fileId, token, sym, importMap, packagePrefix, paramsByMethod);
                    if (td != null) {
                        // For field/parameter declarations where typeFqn is still null, try hover as fallback
                        td = enrichVariableTypeFromHover(td, session, textDocId, token, importMap, packagePrefix);
                        result.add(td);
                    }
                }
                continue;
            }

            // Fix 1: member-access FQN — handles e.g. ImportOption.DoNotIncludeTests where only
            // ImportOption is imported. Build the full FQN from the import map without calling hover.
            String memberFqn = resolveQualifiedMemberAccess(tokens, tokenIdx, importMap);
            if (memberFqn != null) {
                result.add(buildTokenDetail(fileId, token,
                        com.hlag.sourceviewer.domain.model.source.detail.TokenDetailType.TYPE_REF,
                        new com.hlag.sourceviewer.domain.model.source.detail.TypeRefDetail(memberFqn, "CLASS")));
                continue;
            }

            // For reference tokens, call hover and parse the result
            String javaCode = fetchJavaHoverCode(session, textDocId, token);

            // Override: if preceded by @, treat as annotation regardless of hover
            if (annotationPositions.contains(posKey)) {
                String fqn = null;
                if (javaCode != null) {
                    HoverTextParser.ParsedDetail parsed = HoverTextParser.parse(javaCode);
                    if (parsed != null && parsed.type() == com.hlag.sourceviewer.domain.model.source.detail.TokenDetailType.ANNOTATION) {
                        // Hover already gave us an AnnotationDetail — resolve FQN if still simple
                        com.hlag.sourceviewer.domain.model.source.detail.AnnotationDetail ad =
                                (com.hlag.sourceviewer.domain.model.source.detail.AnnotationDetail) parsed.detail();
                        fqn = ad.qualifiedName();
                    } else {
                        fqn = deriveAnnotationFqn(javaCode, token.text());
                    }
                }
                if (fqn == null) fqn = token.text();
                // Resolve simple name via import map
                if (!fqn.contains(".")) fqn = importMap.getOrDefault(fqn, fqn);
                result.add(buildTokenDetail(fileId, token,
                        com.hlag.sourceviewer.domain.model.source.detail.TokenDetailType.ANNOTATION,
                        new com.hlag.sourceviewer.domain.model.source.detail.AnnotationDetail(fqn)));
                continue;
            }

            if (javaCode == null) {
                // Hover returned nothing — fall back to import map / java.lang / same-package for type refs
                String fqn = resolveTypeRef(token.text(), importMap, packagePrefix);
                if (fqn != null) {
                    result.add(buildTokenDetail(fileId, token,
                            com.hlag.sourceviewer.domain.model.source.detail.TokenDetailType.TYPE_REF,
                            new com.hlag.sourceviewer.domain.model.source.detail.TypeRefDetail(fqn, "CLASS")));
                }
                continue;
            }

            HoverTextParser.ParsedDetail parsed = HoverTextParser.parse(javaCode);
            if (parsed != null) {
                // Fix 3: method-name mismatch guard — JDTLS sometimes returns the callee's signature
                // for a local variable (e.g. hover on `fileUri` returns `resolveFileUri(...)`).
                // Detect by checking the parsed method name against the token text.
                if ((parsed.type() == com.hlag.sourceviewer.domain.model.source.detail.TokenDetailType.METHOD_CALL
                        || parsed.type() == com.hlag.sourceviewer.domain.model.source.detail.TokenDetailType.METHOD_DECL)
                        && parsed.detail() instanceof com.hlag.sourceviewer.domain.model.source.detail.MethodDetail md
                        && !md.name().equals(token.text())) {
                    String fqn = resolveTypeRef(token.text(), importMap, packagePrefix);
                    if (fqn != null) {
                        result.add(buildTokenDetail(fileId, token,
                                com.hlag.sourceviewer.domain.model.source.detail.TokenDetailType.TYPE_REF,
                                new com.hlag.sourceviewer.domain.model.source.detail.TypeRefDetail(fqn, "CLASS")));
                    }
                    continue;
                }
                Object detail = resolveDetailFqns(parsed.detail(), importMap, packagePrefix);
                result.add(buildTokenDetail(fileId, token, parsed.type(), detail));
            } else {
                // Hover code present but unparseable — fall back to import map / same-package
                String fqn = resolveTypeRef(token.text(), importMap, packagePrefix);
                if (fqn != null) {
                    result.add(buildTokenDetail(fileId, token,
                            com.hlag.sourceviewer.domain.model.source.detail.TokenDetailType.TYPE_REF,
                            new com.hlag.sourceviewer.domain.model.source.detail.TypeRefDetail(fqn, "CLASS")));
                }
            }
        }
        return result;
    }

    /**
     * Resolves a member-access expression like {@code ImportOption.DoNotIncludeTests} where the
     * qualifier is a key in the import map. Returns {@code qualifier_fqn + "." + token.text()}, or
     * {@code null} if the pattern does not apply.
     * <p>Only fires when the token looks like a class name (starts uppercase, not ALL_CAPS) to
     * avoid false positives on field/method access chains.</p>
     */
    private static String resolveQualifiedMemberAccess(
            List<ExtractedToken> tokens, int index, Map<String, String> importMap) {
        if (!looksLikeClassName(tokens.get(index).text())) return null;
        int j = index - 1;
        while (j >= 0 && tokens.get(j).is(WHITESPACE)) j--;
        if (j < 0 || !tokens.get(j).is(SEPARATOR, ".")) return null;
        j--;
        while (j >= 0 && tokens.get(j).is(WHITESPACE)) j--;
        if (j < 0 || !tokens.get(j).is(IDENTIFIER)) return null;
        String qualifierFqn = importMap.get(tokens.get(j).text());
        if (qualifierFqn == null) return null;
        return qualifierFqn + "." + tokens.get(index).text();
    }

    private com.hlag.sourceviewer.domain.model.source.TokenDetail buildDetailFromSymbol(
            FileIdentifier fileId, ExtractedToken token, Symbol sym,
            Map<String, String> importMap, String packagePrefix,
            Map<String, List<Symbol>> paramsByMethod) {
        com.hlag.sourceviewer.domain.model.source.detail.TokenDetailType type;
        Object detail;
        switch (sym.kind()) {
            case CLASS, INTERFACE, ENUM, RECORD -> {
                type = com.hlag.sourceviewer.domain.model.source.detail.TokenDetailType.TYPE_DECL;
                detail = new com.hlag.sourceviewer.domain.model.source.detail.TypeDeclDetail(
                        sym.qualifiedName().value(),
                        sym.kind().name(),
                        null,   // superclassFqn — filled in by type_hierarchy at query time
                        List.of());
            }
            case METHOD, CONSTRUCTOR -> {
                type = com.hlag.sourceviewer.domain.model.source.detail.TokenDetailType.METHOD_DECL;
                boolean isCtor = sym.kind() == com.hlag.sourceviewer.domain.model.identifier.SymbolKind.CONSTRUCTOR;
                String declaringClass = sym.qualifiedName().value().contains(".")
                        ? sym.qualifiedName().value().substring(0, sym.qualifiedName().value().lastIndexOf('.'))
                        : null;
                List<com.hlag.sourceviewer.domain.model.source.detail.MethodDetail.MethodParam> params =
                        buildMethodParams(sym.qualifiedName().value(), paramsByMethod, importMap, packagePrefix);
                detail = new com.hlag.sourceviewer.domain.model.source.detail.MethodDetail(
                        token.text(), declaringClass, null, params, isCtor);
            }
            case FIELD, CONSTANT, ENUM_CONSTANT -> {
                type = com.hlag.sourceviewer.domain.model.source.detail.TokenDetailType.VARIABLE;
                String rawType = sym.signature().map(s -> s.value()).orElse(null);
                String typeFqn = resolveRawTypeName(rawType, importMap, packagePrefix);
                String variableKind = sym.kind() == com.hlag.sourceviewer.domain.model.identifier.SymbolKind.ENUM_CONSTANT
                        ? "ENUM_CONSTANT"
                        : sym.kind() == com.hlag.sourceviewer.domain.model.identifier.SymbolKind.CONSTANT ? "CONSTANT" : "FIELD";
                detail = new com.hlag.sourceviewer.domain.model.source.detail.VariableDetail(token.text(), variableKind, typeFqn);
            }
            case LOCAL_VARIABLE -> {
                type = com.hlag.sourceviewer.domain.model.source.detail.TokenDetailType.VARIABLE;
                detail = new com.hlag.sourceviewer.domain.model.source.detail.VariableDetail(token.text(), "LOCAL", null);
            }
            case PARAMETER -> {
                type = com.hlag.sourceviewer.domain.model.source.detail.TokenDetailType.VARIABLE;
                String rawType = sym.signature().map(s -> s.value()).orElse(null);
                String typeFqn = resolveRawTypeName(rawType, importMap, packagePrefix);
                detail = new com.hlag.sourceviewer.domain.model.source.detail.VariableDetail(token.text(), "PARAMETER", typeFqn);
            }
            default -> {
                return null;
            }
        }
        return buildTokenDetail(fileId, token, type, detail);
    }

    private static List<com.hlag.sourceviewer.domain.model.source.detail.MethodDetail.MethodParam> buildMethodParams(
            String methodFqn, Map<String, List<Symbol>> paramsByMethod,
            Map<String, String> importMap, String packagePrefix) {
        List<Symbol> params = paramsByMethod.getOrDefault(methodFqn, List.of());
        if (params.isEmpty()) return List.of();
        return params.stream()
                .map(p -> {
                    String rawType = p.signature().map(s -> s.value()).orElse(null);
                    String typeName = resolveRawTypeName(rawType, importMap, packagePrefix);
                    return new com.hlag.sourceviewer.domain.model.source.detail.MethodDetail.MethodParam(
                            p.name().value(), typeName != null ? typeName : (rawType != null ? rawType : "?"));
                })
                .toList();
    }

    /** Tries to call hover for a VARIABLE declaration whose typeFqn is still null. */
    private com.hlag.sourceviewer.domain.model.source.TokenDetail enrichVariableTypeFromHover(
            com.hlag.sourceviewer.domain.model.source.TokenDetail td,
            LanguageServerSession<?> session, TextDocumentIdentifier textDocId,
            ExtractedToken token, Map<String, String> importMap, String packagePrefix) {
        if (!"VARIABLE".equals(td.detailType())) return td;
        try {
            java.util.Map<String, Object> detailMap = jsonMapper.parseToMap(td.detail());
            if (detailMap.get("typeFqn") != null) return td; // already resolved
            String javaCode = fetchJavaHoverCode(session, textDocId, token);
            if (javaCode == null) return td;
            // Try FIELD_WITH_CLASS: "modifiers type DeclaringClass.fieldName"
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("^(?:[\\w ]+?\\s+)?(\\S+)\\s+(?:[\\w$]+\\.)+[\\w$]+$")
                    .matcher(javaCode);
            if (m.find()) {
                String rawType = m.group(1);
                String resolved = resolveRawTypeName(rawType, importMap, packagePrefix);
                if (resolved != null) {
                    detailMap.put("typeFqn", resolved);
                    String newJson = jsonMapper.serialize(detailMap);
                    return new com.hlag.sourceviewer.domain.model.source.TokenDetail(
                            td.fileIdentifier(), td.line(), td.columnStart(), td.detailType(), newJson);
                }
            }
            // Fallback: HoverTextParser handles "static final String DOMAIN" (no class qualifier)
            HoverTextParser.ParsedDetail hoverParsed = HoverTextParser.parse(javaCode);
            if (hoverParsed != null
                    && hoverParsed.detail() instanceof com.hlag.sourceviewer.domain.model.source.detail.VariableDetail vd
                    && vd.typeFqn() != null) {
                String resolved = resolveRawTypeName(vd.typeFqn(), importMap, packagePrefix);
                if (resolved != null) {
                    detailMap.put("typeFqn", resolved);
                    String newJson = jsonMapper.serialize(detailMap);
                    return new com.hlag.sourceviewer.domain.model.source.TokenDetail(
                            td.fileIdentifier(), td.line(), td.columnStart(), td.detailType(), newJson);
                }
            }
        } catch (Exception ignored) {}
        return td;
    }

    /**
     * Resolves a raw type name (as returned by JDTLS DocumentSymbol.getDetail()) to a FQN.
     * Strips generics and array brackets, then checks the import map, java.lang, and same-package.
     * Returns the primitive name for primitives (useful for display). Returns null for empty/blank.
     */
    private static String resolveRawTypeName(String rawType, Map<String, String> importMap, String packagePrefix) {
        if (rawType == null || rawType.isBlank()) return null;
        // Strip generics and arrays: "List<String>" → "List", "String[]" → "String"
        String base = rawType;
        int idx = base.indexOf('<');
        if (idx >= 0) base = base.substring(0, idx);
        idx = base.indexOf('[');
        if (idx >= 0) base = base.substring(0, idx);
        base = base.strip();
        if (base.isEmpty()) return null;
        if (isPrimitive(base)) return base; // display as-is, no navigation link possible
        if (base.contains(".")) return base; // already qualified
        String resolved = resolveTypeRef(base, importMap, packagePrefix);
        return resolved != null ? resolved : base; // return simple name as fallback for display
    }

    private static boolean isPrimitive(String name) {
        return switch (name) {
            case "int", "long", "double", "float", "boolean", "byte", "char", "short", "void" -> true;
            default -> false;
        };
    }

    private com.hlag.sourceviewer.domain.model.source.TokenDetail buildTokenDetail(
            FileIdentifier fileId, ExtractedToken token,
            com.hlag.sourceviewer.domain.model.source.detail.TokenDetailType type, Object detail) {
        return new com.hlag.sourceviewer.domain.model.source.TokenDetail(
                fileId, token.line(), token.columnStart(),
                type.name(), HoverTextParser.toJson(detail, jsonMapper));
    }

    private String fetchJavaHoverCode(LanguageServerSession<?> session,
                                       TextDocumentIdentifier textDocId,
                                       ExtractedToken token) {
        try {
            HoverParams params = new HoverParams(textDocId, toPosition(token));
            org.eclipse.lsp4j.Hover hover = session.textDocumentService()
                    .hover(params)
                    .get(HOVER_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (hover == null || hover.getContents() == null) return null;
            return extractJavaCode(hover);
        } catch (Exception e) {
            logger.trace("[JDTLS] hover failed for '{}' at {}:{}: {}",
                    token.text(), token.line(), token.columnStart(), e.getMessage());
            return null;
        }
    }

    /** Extracts the first Java-language code block from a hover response. */
    private static String extractJavaCode(org.eclipse.lsp4j.Hover hover) {
        var contents = hover.getContents();
        if (contents == null) return null;

        // Either<List<Either<String, MarkedString>>, MarkupContent>
        if (contents.isRight()) {
            // MarkupContent: may contain markdown fenced code blocks
            String value = contents.getRight().getValue();
            if (value == null) return null;
            // Extract ```java ... ``` blocks
            int start = value.indexOf("```java");
            if (start >= 0) {
                int end = value.indexOf("```", start + 7);
                if (end > start) {
                    return value.substring(start + 7, end).strip();
                }
            }
            return value.strip();
        } else {
            List<Either<String, MarkedString>> left = contents.getLeft();
            if (left == null) return null;
            for (Either<String, MarkedString> item : left) {
                if (item.isRight()) {
                    MarkedString ms = item.getRight();
                    if ("java".equals(ms.getLanguage())) {
                        return ms.getValue();
                    }
                }
            }
            // Fallback: plain string
            for (Either<String, MarkedString> item : left) {
                if (item.isLeft() && item.getLeft() != null && !item.getLeft().isBlank()) {
                    return item.getLeft().strip();
                }
            }
            return null;
        }
    }

    private static String deriveAnnotationFqn(String javaCode, String fallback) {
        // Try "class FQN" pattern (annotations sometimes appear as class in hover)
        Matcher m = Pattern.compile("(?:class|interface)\\s+(\\S+)").matcher(javaCode);
        if (m.find()) return m.group(1);
        return fallback;
    }

    /** Resolves simple-name FQN fields in a parsed detail using the import map and package prefix. */
    private static Object resolveDetailFqns(Object detail, Map<String, String> importMap, String packagePrefix) {
        if (detail instanceof com.hlag.sourceviewer.domain.model.source.detail.MethodDetail md) {
            String dc = md.declaringClass();
            if (dc != null && !dc.contains(".")) {
                dc = resolveTypeRef(dc, importMap, packagePrefix);
                if (dc == null) dc = md.declaringClass();
            }
            if (!Objects.equals(dc, md.declaringClass())) {
                return new com.hlag.sourceviewer.domain.model.source.detail.MethodDetail(
                        md.name(), dc, md.returnType(), md.parameters(), md.isConstructor());
            }
        } else if (detail instanceof com.hlag.sourceviewer.domain.model.source.detail.TypeRefDetail tr) {
            String fqn = tr.qualifiedName();
            if (!fqn.contains(".")) {
                String resolved = resolveTypeRef(fqn, importMap, packagePrefix);
                if (resolved != null) return new com.hlag.sourceviewer.domain.model.source.detail.TypeRefDetail(resolved, tr.kind());
            }
        } else if (detail instanceof com.hlag.sourceviewer.domain.model.source.detail.VariableDetail vd) {
            String typeFqn = vd.typeFqn();
            if (typeFqn != null && !typeFqn.contains(".")) {
                String resolved = resolveTypeRef(typeFqn, importMap, packagePrefix);
                if (resolved != null) return new com.hlag.sourceviewer.domain.model.source.detail.VariableDetail(vd.name(), vd.variableKind(), resolved);
            }
        }
        return detail;
    }

    /**
     * Resolves a simple type name to a FQN using: import map → java.lang → same package.
     * Returns null if the name is a primitive or cannot be resolved.
     */
    private static String resolveTypeRef(String name, Map<String, String> importMap, String packagePrefix) {
        if (name == null || name.isBlank() || name.contains(".")) return name;
        String fqn = importMap.get(name);
        if (fqn != null) return fqn;
        fqn = javaLangFqn(name);
        if (fqn != null) return fqn;
        if (looksLikeClassName(name) && packagePrefix != null && !packagePrefix.isEmpty()) {
            return packagePrefix + name;
        }
        return null;
    }

    /** True if the name looks like a Java class name (starts uppercase, not ALL_CAPS constant). */
    private static boolean looksLikeClassName(String name) {
        if (name == null || name.isEmpty() || !Character.isUpperCase(name.charAt(0))) return false;
        // ALL_CAPS identifiers (e.g. CLASS_BODY_UNKNOWN, DOMAIN) are constants, not class names
        return !name.equals(name.toUpperCase(java.util.Locale.ROOT));
    }

    /** Creates TYPE_REF token details for every identifier in each non-wildcard import statement. */
    private List<com.hlag.sourceviewer.domain.model.source.TokenDetail> extractImportTokenDetails(
            List<ExtractedToken> tokens, FileIdentifier fileId, Map<String, String> importMap) {

        List<com.hlag.sourceviewer.domain.model.source.TokenDetail> result = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            if (!tokens.get(i).is(KEYWORD, "import")) continue;
            // Collect all identifiers in this import statement, determine the FQN
            List<ExtractedToken> identifiers = new ArrayList<>();
            boolean wildcard = false;
            for (int j = i + 1; j < tokens.size(); j++) {
                ExtractedToken t = tokens.get(j);
                if (t.is(SEPARATOR, ";")) break;
                if (t.is(OPERATOR, "*")) { wildcard = true; break; }
                if (t.is(IDENTIFIER)) identifiers.add(t);
            }
            if (!wildcard && !identifiers.isEmpty()) {
                // The FQN is keyed by the last identifier (the type's simple name)
                String fqn = importMap.get(identifiers.getLast().text());
                if (fqn != null) {
                    // Make every identifier in the import clickable, all pointing to the same type
                    for (ExtractedToken ident : identifiers) {
                        result.add(buildTokenDetail(fileId, ident,
                                com.hlag.sourceviewer.domain.model.source.detail.TokenDetailType.TYPE_REF,
                                new com.hlag.sourceviewer.domain.model.source.detail.TypeRefDetail(fqn, "CLASS")));
                    }
                }
            }
        }
        return result;
    }

    // -- Highlight group computation -------------------------------------------

    private static Map<String, Integer> computeHighlightGroups(
            List<PendingReference> references,
            FilePath filePath,
            Map<String, Symbol> symbolsByPosition) {

        Map<String, Integer> defKeyToGroupId = new HashMap<>();
        Map<String, Integer> tokenPosToGroupId = new HashMap<>();
        int[] nextId = {1};

        // First, assign group IDs to declaration positions so they share the group
        // with all their references (declaration IS the definition).
        for (Map.Entry<String, Symbol> entry : symbolsByPosition.entrySet()) {
            String posKey = entry.getKey();
            String defKey = filePath.value() + ":" + posKey;
            defKeyToGroupId.computeIfAbsent(defKey, k -> nextId[0]++);
            tokenPosToGroupId.put(posKey, defKeyToGroupId.get(defKey));
        }

        // Assign reference tokens the same group as their definition
        for (PendingReference ref : references) {
            if (ref.line().isEmpty() || ref.column().isEmpty()) continue;
            if (ref.definitionFilePath().isEmpty() || ref.definitionLine().isEmpty() || ref.definitionColumn().isEmpty()) continue;

            String defKey = ref.definitionFilePath().get().value()
                    + ":" + ref.definitionLine().get().value()
                    + ":" + ref.definitionColumn().get().value();
            int groupId = defKeyToGroupId.computeIfAbsent(defKey, k -> nextId[0]++);
            String tokenPos = ref.line().get().value() + ":" + ref.column().get().value();
            tokenPosToGroupId.put(tokenPos, groupId);
        }

        return tokenPosToGroupId;
    }

    // -- Type hierarchy extraction (ANTLR token stream) ------------------------

    private static List<com.hlag.sourceviewer.domain.model.source.TypeHierarchyEntry> extractTypeHierarchy(
            List<ExtractedToken> tokens, String packagePrefix, Map<String, String> importMap) {

        List<com.hlag.sourceviewer.domain.model.source.TypeHierarchyEntry> result = new ArrayList<>();
        List<String> typeStack = new ArrayList<>();
        int braceDepth = 0;
        List<String> currentBraceDepthStack = new ArrayList<>();

        for (int i = 0; i < tokens.size(); i++) {
            ExtractedToken tok = tokens.get(i);

            if (tok.is(SEPARATOR, "{")) {
                braceDepth++;
                continue;
            }
            if (tok.is(SEPARATOR, "}")) {
                if (!currentBraceDepthStack.isEmpty()) {
                    int lastDepth = Integer.parseInt(currentBraceDepthStack.getLast());
                    if (braceDepth == lastDepth) {
                        typeStack.removeLast();
                        currentBraceDepthStack.removeLast();
                    }
                }
                braceDepth--;
                continue;
            }

            if (!tok.is(KEYWORD)) continue;
            String kw = tok.text();
            if (!TYPE_KEYWORDS.contains(kw)) continue;

            // Find type name identifier
            int nameIdx = nextNonWhitespace(tokens, i + 1);
            if (nameIdx < 0 || !tokens.get(nameIdx).is(IDENTIFIER)) continue;
            String simpleName = tokens.get(nameIdx).text();
            String typeFqn = buildTypeFqn(packagePrefix, typeStack, simpleName);
            typeStack.add(simpleName);
            currentBraceDepthStack.add(String.valueOf(braceDepth + 1));

            // Scan for extends/implements until we hit '{'
            int j = nameIdx + 1;
            boolean inExtends = false;
            boolean inImplements = false;
            List<String> currentTypeList = new ArrayList<>();

            while (j < tokens.size()) {
                ExtractedToken t = tokens.get(j);
                if (t.is(SEPARATOR, "{") || t.is(SEPARATOR, ";")) break;
                if (t.is(KEYWORD, "extends")) {
                    if (!currentTypeList.isEmpty()) {
                        addHierarchyEntries(result, typeFqn, currentTypeList, inExtends ? "EXTENDS" : "IMPLEMENTS", importMap, packagePrefix);
                        currentTypeList.clear();
                    }
                    inExtends = true; inImplements = false;
                } else if (t.is(KEYWORD, "implements")) {
                    if (!currentTypeList.isEmpty()) {
                        addHierarchyEntries(result, typeFqn, currentTypeList, inExtends ? "EXTENDS" : "IMPLEMENTS", importMap, packagePrefix);
                        currentTypeList.clear();
                    }
                    inImplements = true; inExtends = false;
                } else if ((inExtends || inImplements) && t.is(IDENTIFIER)) {
                    // Collect type name (may have generic params we skip)
                    StringBuilder typeName = new StringBuilder(t.text());
                    int k = j + 1;
                    while (k < tokens.size() && tokens.get(k).is(SEPARATOR, ".")) {
                        if (k + 1 < tokens.size() && tokens.get(k + 1).is(IDENTIFIER)) {
                            typeName.append('.').append(tokens.get(k + 1).text());
                            k += 2;
                        } else break;
                    }
                    currentTypeList.add(typeName.toString());
                    j = k;
                    // Skip generic type params <...>
                    if (j < tokens.size() && tokens.get(j).is(OPERATOR, "<")) {
                        int depth = 1;
                        j++;
                        while (j < tokens.size() && depth > 0) {
                            if (tokens.get(j).is(OPERATOR, "<")) depth++;
                            else if (tokens.get(j).is(OPERATOR, ">")) depth--;
                            j++;
                        }
                    }
                    // Check for comma (multiple implements)
                    if (j < tokens.size() && tokens.get(j).is(SEPARATOR, ",")) {
                        j++;
                    }
                    continue;
                }
                j++;
            }
            if (!currentTypeList.isEmpty()) {
                addHierarchyEntries(result, typeFqn, currentTypeList, inExtends ? "EXTENDS" : "IMPLEMENTS", importMap, packagePrefix);
            }
            i = nameIdx;
        }
        return result;
    }

    private static void addHierarchyEntries(
            List<com.hlag.sourceviewer.domain.model.source.TypeHierarchyEntry> out,
            String subtypeFqn, List<String> supertypes, String relationship,
            Map<String, String> importMap, String packagePrefix) {
        for (String simpleName : supertypes) {
            String superFqn = resolveTypeFqn(simpleName, importMap, packagePrefix);
            out.add(new com.hlag.sourceviewer.domain.model.source.TypeHierarchyEntry(
                    subtypeFqn, superFqn, relationship));
        }
    }

    private static String buildTypeFqn(String packagePrefix, List<String> typeStack, String simpleName) {
        if (typeStack.isEmpty()) return packagePrefix + simpleName;
        return packagePrefix + String.join(".", typeStack) + "." + simpleName;
    }

    private static String resolveTypeFqn(String name, Map<String, String> importMap, String packagePrefix) {
        if (name.contains(".")) return name; // already qualified
        String fromImport = importMap.get(name);
        if (fromImport != null) return fromImport;
        // Common java.lang types
        String javaLang = javaLangFqn(name);
        if (javaLang != null) return javaLang;
        return packagePrefix + name;
    }

    private static String javaLangFqn(String name) {
        return switch (name) {
            case "Object"     -> "java.lang.Object";
            case "String"     -> "java.lang.String";
            case "Number"     -> "java.lang.Number";
            case "Comparable" -> "java.lang.Comparable";
            case "Iterable"   -> "java.lang.Iterable";
            case "Runnable"   -> "java.lang.Runnable";
            case "Cloneable"  -> "java.lang.Cloneable";
            case "Serializable" -> "java.io.Serializable";
            default           -> null;
        };
    }

    private static Map<String, String> buildImportMap(List<ExtractedToken> tokens) {
        Map<String, String> importMap = new HashMap<>();
        for (int i = 0; i < tokens.size(); i++) {
            ExtractedToken tok = tokens.get(i);
            if (!tok.is(KEYWORD, "import")) continue;

            StringBuilder fqn = new StringBuilder();
            int j = i + 1;
            while (j < tokens.size()) {
                ExtractedToken t = tokens.get(j++);
                if (t.is(WHITESPACE)) continue;
                if (t.is(KEYWORD, "static")) continue;
                if (t.is(IDENTIFIER)) fqn.append(t.text());
                else if (t.is(SEPARATOR, ".")) fqn.append('.');
                else if (t.is(OPERATOR, "*")) break;
                else if (t.is(SEPARATOR, ";")) break;
                else break;
            }
            String fqnStr = fqn.toString();
            if (fqnStr.contains(".")) {
                String simpleName = fqnStr.substring(fqnStr.lastIndexOf('.') + 1);
                importMap.put(simpleName, fqnStr);
            }
        }
        return importMap;
    }

    private static int nextNonWhitespace(List<ExtractedToken> tokens, int from) {
        for (int i = from; i < tokens.size(); i++) {
            if (!tokens.get(i).is(WHITESPACE)) return i;
        }
        return -1;
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
                        flattenDocumentSymbol(fileId, either.getRight(), proposedPackagePrefix, null, symbols);
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
                                flattenDocumentSymbol(fileId, documentSymbol, packagePrefix, null, symbols));
            }
            return symbols;
        } catch (Exception e) {
            logger.debug("[JDTLS] documentSymbol failed for {}: {}", fileUri, e.getMessage());
            return List.of();
        }
    }

    private void flattenDocumentSymbol(FileIdentifier fileId, DocumentSymbol documentSymbol,
                                       String parentFullQualifiedName, SymbolKind parentKind,
                                       List<Symbol> out) {
        org.eclipse.lsp4j.SymbolKind lspKind = documentSymbol.getKind();
        SymbolKind kind = mapLspSymbolKind(lspKind);
        // JDTLS has no Parameter SymbolKind in LSP spec; parameters appear as Variable children of
        // methods and constructors. Detect them by checking the parent's mapped kind.
        if (kind == SymbolKind.LOCAL_VARIABLE
                && (parentKind == SymbolKind.METHOD || parentKind == SymbolKind.CONSTRUCTOR)) {
            kind = SymbolKind.PARAMETER;
        }
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
                flattenDocumentSymbol(fileId, child, fullQualifiedName, kind, out);
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

    private static Position toPosition(ExtractedToken token) {
        return new Position(token.line() - 1, Math.max(0, token.columnStart() - 1));
    }

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
                    enriched.add(token);
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
                // Emit tokens from startIndex to endIndex with groupId only (FQN stored in token_detail)
                tokens.subList(startIndex, endIndex).forEach(importToken ->
                    result.add(importToken.withGroupId(groupId))
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
