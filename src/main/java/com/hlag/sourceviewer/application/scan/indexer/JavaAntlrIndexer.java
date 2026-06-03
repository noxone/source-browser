package com.hlag.sourceviewer.application.scan.indexer;

import com.hlag.sourceviewer.application.scan.ParsedFile;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.hlag.sourceviewer.domain.model.source.ExtractedToken.TokenKind.*;

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

    private static final Set<String> TYPE_KEYWORDS = Set.of("class", "interface", "enum", "record");
    private static final Set<String> MEMBER_MODIFIERS = Set.of(
            "public", "private", "protected", "static", "final", "abstract", "native", "synchronized",
            "strictfp", "default", "transient", "volatile");

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
        return path.isJavaFile();
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
        } catch (Exception ignored) {
            // Fall through to repo root when descriptor discovery fails.
        }

        return repoRoot;
    }

    private static boolean hasBuildDescriptor(Path dir) {
        return Files.isRegularFile(dir.resolve("pom.xml"))
                || Files.isRegularFile(dir.resolve("build.gradle"))
                || Files.isRegularFile(dir.resolve("build.gradle.kts"))
                || Files.isRegularFile(dir.resolve("settings.gradle"))
                || Files.isRegularFile(dir.resolve("settings.gradle.kts"));
    }

    private static boolean isBuildDescriptor(Path file) {
        String name = file.getFileName().toString();
        return "pom.xml".equals(name)
                || "build.gradle".equals(name)
                || "build.gradle.kts".equals(name)
                || "settings.gradle".equals(name)
                || "settings.gradle.kts".equals(name);
    }

    @SuppressWarnings("unchecked")
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
     * Indexes the file via ANTLR tokenisation and, when a JDTLS session is available,
     * additionally queries the language server for hover details on every token and
     * writes the results to the log.
     */
    @Override
    public ParsedFile indexFile(FileIdentifier fileId, FilePath path, String content, Object context) {
        ParsedFile parsedFile = super.indexFile(fileId, path, content, context);

        if (context instanceof JavaAntlrIndexingContext ctx && ctx.session().isPresent()) {
            parsedFile = enrichWithHover(path, content, parsedFile, ctx.session().get());
        }

        return parsedFile;
    }

    /** Closes the JDTLS session held by the context (if any). */
    @Override
    public void teardown(Object context) {
        if (context instanceof JavaAntlrIndexingContext ctx) {
            ctx.close();
            logger.debug("JDTLS session closed after indexing");
        }
    }

    // -- LSP hover queries -----------------------------------------------------

    private ParsedFile enrichWithHover(FilePath path, String content, ParsedFile parsedFile,
                                       LanguageServerSession<? extends DiagnosticsCapable> session) {
        final var fileUri = resolveFileUri(session.projectRoot(), path);
        openDocument(session, fileUri, content);
        try {
            session.languageClient().awaitDiagnostics(fileUri, 5, TimeUnit.SECONDS);

            int queried = 0;
            int withContent = 0;
            int emptyOrNull = 0;
            int failed = 0;
            List<ExtractedToken> enriched = new ArrayList<>(parsedFile.tokens().size());

            for (ExtractedToken token : parsedFile.tokens()) {
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
                                token.text(), token.kind(), token.qualifiedName(), token.symbolId(), hoverText));
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

            String filename = Path.of(java.net.URI.create(fileUri)).getFileName().toString();
            logger.debug("[JDTLS] {}: {} identifiers queried, {} with hover, {} empty/null, {} failed",
                    filename, queried, withContent, emptyOrNull, failed);

            return new ParsedFile(parsedFile.declarations(), parsedFile.references(), enriched);
        } finally {
            closeDocument(session, fileUri);
        }
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

    private static void openDocument(LanguageServerSession<? extends DiagnosticsCapable> session, String fileUri, String content) {
        // Register latch before didOpen — JDTLS may publish diagnostics immediately
        session.languageClient().waitForDiagnostics(fileUri);
        TextDocumentItem item = new TextDocumentItem(fileUri, "java", 1, content);
        session.textDocumentService().didOpen(new DidOpenTextDocumentParams(item));
    }

    private static void closeDocument(LanguageServerSession<?> session, String fileUri) {
        session.textDocumentService().didClose(
                new DidCloseTextDocumentParams(new TextDocumentIdentifier(fileUri)));
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
        if (value instanceof org.eclipse.lsp4j.MarkedString markedString) {
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
                i = closeParenIndex;
            }
        }
        return symbols;
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
