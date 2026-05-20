package com.hlag.sourceviewer.application.scan;

import com.github.javaparser.JavaParser;
import com.github.javaparser.JavaToken;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.hlag.sourceviewer.domain.model.identifier.ColumnNumber;
import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.source.ExtractedToken;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import com.hlag.sourceviewer.domain.model.identifier.LineNumber;
import com.hlag.sourceviewer.domain.model.identifier.QualifiedName;
import com.hlag.sourceviewer.domain.model.identifier.ReferenceKind;
import com.hlag.sourceviewer.domain.model.identifier.SimpleName;
import com.hlag.sourceviewer.domain.model.identifier.SymbolKind;
import com.hlag.sourceviewer.domain.model.source.Symbol;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Parses Java source files using JavaParser and extracts symbol declarations
 * and symbol references (with resolution where possible).
 *
 * <p>This class is stateless — the TypeSolver is created per scan and passed in,
 * and each {@link #parse} call creates a fresh {@link JavaParser} instance for
 * thread safety.</p>
 */
@ApplicationScoped
public class JavaFileParser {

    private static final Logger logger = LoggerFactory.getLogger(JavaFileParser.class);

    /**
     * Builds a {@link TypeSolver} for the given repository checkout directory.
     * Walks the directory tree to discover all {@code src/main/java} and
     * {@code src/test/java} roots at any depth (supporting single-module and
     * multi-module repos). Skips {@code .git}, {@code target}, {@code build},
     * and {@code node_modules} directories for efficiency.
     * Always includes a {@link ReflectionTypeSolver} for JDK and classpath library types.
     */
    public TypeSolver buildTypeSolver(Path repoLocalPath) {
        return buildCombinedTypeSolver(repoLocalPath);
    }

    /**
     * Same as {@link #buildTypeSolver} but returns the mutable {@link CombinedTypeSolver}
     * so callers can append additional solvers (e.g. {@code JarTypeSolver} for Maven artifacts).
     */
    public CombinedTypeSolver buildCombinedTypeSolver(Path repoLocalPath) {
        var solver = new CombinedTypeSolver();
        solver.add(new ReflectionTypeSolver(false));
        List<Path> sourceRoots = findSourceRoots(repoLocalPath);
        sourceRoots.forEach(root -> {
            solver.add(new JavaParserTypeSolver(root));
            logger.debug("Added JavaParserTypeSolver for {}", root);
        });
        if (sourceRoots.isEmpty()) {
            logger.debug("No Java source roots found under {}", repoLocalPath);
        }
        return solver;
    }

    private static List<Path> findSourceRoots(Path repoLocalPath) {
        var roots = new ArrayList<Path>();
        try {
            Files.walkFileTree(repoLocalPath, Set.of(), 15, new SimpleFileVisitor<>() {
                private static final Set<String> SKIP_DIRS =
                        Set.of(".git", "target", "build", "node_modules", ".gradle", ".idea");

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String name = dir.getFileName() != null ? dir.getFileName().toString() : "";
                    if (SKIP_DIRS.contains(name)) return FileVisitResult.SKIP_SUBTREE;
                    if (isJavaSourceRoot(dir)) {
                        roots.add(dir);
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            logger.warn("Could not discover source roots under {}: {}", repoLocalPath, e.getMessage());
        }
        return roots;
    }

    /** Returns true when {@code path} matches the pattern {@code .../src/{main,test}/java}. */
    private static boolean isJavaSourceRoot(Path path) {
        if (path.getFileName() == null || !path.getFileName().toString().equals("java")) return false;
        Path parent = path.getParent();
        if (parent == null || parent.getFileName() == null) return false;
        String parentName = parent.getFileName().toString();
        if (!parentName.equals("main") && !parentName.equals("test")) return false;
        Path grandParent = parent.getParent();
        return grandParent != null
                && grandParent.getFileName() != null
                && grandParent.getFileName().toString().equals("src");
    }

    /**
     * Parses the given Java source file content and extracts all symbol declarations
     * and references. Resolution failures are handled gracefully — unresolved references
     * are stored with their simple name.
     */
    public ParsedFile parse(FileIdentifier fileId, FilePath filePath, String content, TypeSolver typeSolver) {
        try {
            var config = new ParserConfiguration()
                    .setSymbolResolver(new JavaSymbolSolver(typeSolver))
                    	.setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);
            var result = new JavaParser(config).parse(content);
            if (result.getResult().isEmpty()) {
                logger.warn("No parse result for {}", filePath.value());
                return ParsedFile.empty();
            }
            CompilationUnit cu = result.getResult().get();
            String packagePrefix = cu.getPackageDeclaration()
                    .map(pd -> pd.getNameAsString() + ".")
                    .orElse("");

            var declarations = new ArrayList<Symbol>();
            var references = new ArrayList<PendingReference>();
            cu.accept(new DeclarationVisitor(fileId, packagePrefix, declarations), new ArrayDeque<>());
            cu.accept(new ReferenceVisitor(fileId, references), null);

            List<ExtractedToken> tokens = extractTokens(cu);

            return new ParsedFile(
                    Collections.unmodifiableList(declarations),
                    Collections.unmodifiableList(references),
                    Collections.unmodifiableList(tokens));
        } catch (Exception e) {
            logger.warn("Could not parse Java file {}: {}", filePath.value(), e.getMessage());
            return ParsedFile.empty();
        }
    }

    // ── Token extraction ──────────────────────────────────────────────────────

    private static List<ExtractedToken> extractTokens(CompilationUnit cu) {
        return cu.getTokenRange()
                .map(range -> {
                    var result = new ArrayList<ExtractedToken>();
                    for (JavaToken token : range) {
                        var r = token.getRange().orElse(null);
                        if (r == null) continue;
                        result.add(new ExtractedToken(
                                r.begin.line,
                                r.begin.column,
                                r.end.column,
                                token.getText(),
                                mapTokenKind(token),
                                null,
                                null));
                    }
                    return (List<ExtractedToken>) result;
                })
                .orElse(List.of());
    }

    private static ExtractedToken.TokenKind mapTokenKind(JavaToken token) {
        return switch (token.getCategory()) {
            case KEYWORD -> ExtractedToken.TokenKind.KEYWORD;
            case IDENTIFIER -> ExtractedToken.TokenKind.IDENTIFIER;
            case SEPARATOR -> ExtractedToken.TokenKind.SEPARATOR;
            case OPERATOR -> ExtractedToken.TokenKind.OPERATOR;
            case WHITESPACE_NO_EOL, EOL -> ExtractedToken.TokenKind.WHITESPACE;
            case COMMENT -> {
                JavaToken.Kind kind = JavaToken.Kind.valueOf(token.getKind());
                yield switch (kind) {
                    case SINGLE_LINE_COMMENT -> ExtractedToken.TokenKind.LINE_COMMENT;
                    case JAVADOC_COMMENT -> ExtractedToken.TokenKind.JAVADOC_COMMENT;
                    default -> ExtractedToken.TokenKind.BLOCK_COMMENT;
                };
            }
            case LITERAL -> {
                JavaToken.Kind kind = JavaToken.Kind.valueOf(token.getKind());
                yield switch (kind) {
                    case CHARACTER_LITERAL -> ExtractedToken.TokenKind.CHAR_LITERAL;
                    case STRING_LITERAL, TEXT_BLOCK_LITERAL, TEXT_BLOCK_CONTENT,
                         ENTER_TEXT_BLOCK -> ExtractedToken.TokenKind.STRING_LITERAL;
                    case LONG_LITERAL -> ExtractedToken.TokenKind.LONG_LITERAL;
                    case FLOATING_POINT_LITERAL, DECIMAL_FLOATING_POINT_LITERAL,
                         HEXADECIMAL_FLOATING_POINT_LITERAL -> ExtractedToken.TokenKind.FLOAT_LITERAL;
                    default -> ExtractedToken.TokenKind.INTEGER_LITERAL;
                };
            }
            default -> ExtractedToken.TokenKind.OTHER;
        };
    }

    // ── Declaration visitor ───────────────────────────────────────────────────

    private static final class DeclarationVisitor extends VoidVisitorAdapter<Deque<String>> {

        private final FileIdentifier fileId;
        private final String packagePrefix;
        private final List<Symbol> symbols;

        DeclarationVisitor(FileIdentifier fileId, String packagePrefix, List<Symbol> symbols) {
            this.fileId = fileId;
            this.packagePrefix = packagePrefix;
            this.symbols = symbols;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration n, Deque<String> stack) {
            SymbolKind kind = n.isInterface() ? SymbolKind.INTERFACE : SymbolKind.CLASS;
            addDecl(n.getNameAsString(), kind, Optional.empty(), n, modifiers(n.getModifiers()), stack);
            stack.push(n.getNameAsString());
            super.visit(n, stack);
            stack.pop();
        }

        @Override
        public void visit(EnumDeclaration n, Deque<String> stack) {
            addDecl(n.getNameAsString(), SymbolKind.ENUM, Optional.empty(), n, modifiers(n.getModifiers()), stack);
            stack.push(n.getNameAsString());
            super.visit(n, stack);
            stack.pop();
        }

        @Override
        public void visit(RecordDeclaration n, Deque<String> stack) {
            addDecl(n.getNameAsString(), SymbolKind.RECORD, Optional.empty(), n, modifiers(n.getModifiers()), stack);
            stack.push(n.getNameAsString());
            super.visit(n, stack);
            stack.pop();
        }

        @Override
        public void visit(AnnotationDeclaration n, Deque<String> stack) {
            addDecl(n.getNameAsString(), SymbolKind.ANNOTATION_TYPE, Optional.empty(), n, modifiers(n.getModifiers()), stack);
            stack.push(n.getNameAsString());
            super.visit(n, stack);
            stack.pop();
        }

        @Override
        public void visit(MethodDeclaration n, Deque<String> stack) {
            String sig = buildSignature(n.getNameAsString(), n.getParameters());
            addDecl(n.getNameAsString(), SymbolKind.METHOD, Optional.of(sig), n, modifiers(n.getModifiers()), stack);
            super.visit(n, stack);
        }

        @Override
        public void visit(ConstructorDeclaration n, Deque<String> stack) {
            String sig = buildSignature(n.getNameAsString(), n.getParameters());
            addDecl(n.getNameAsString(), SymbolKind.CONSTRUCTOR, Optional.of(sig), n, modifiers(n.getModifiers()), stack);
            super.visit(n, stack);
        }

        @Override
        public void visit(FieldDeclaration n, Deque<String> stack) {
            List<String> mods = modifiers(n.getModifiers());
            for (var variable : n.getVariables()) {
                addDecl(variable.getNameAsString(), SymbolKind.FIELD, Optional.empty(), n, mods, stack);
            }
            super.visit(n, stack);
        }

        private void addDecl(String simpleName, SymbolKind kind, Optional<String> signature,
                              com.github.javaparser.ast.Node node, List<String> mods,
                              Deque<String> stack) {
            symbols.add(new Symbol(
                    fileId, kind,
                    new SimpleName(simpleName),
                    new QualifiedName(qualifiedName(stack, simpleName)),
                    signature.map(SimpleName::new),
                    node.getRange().map(r -> new LineNumber(r.begin.line)),
                    node.getRange().map(r -> new LineNumber(r.end.line)),
                    node.getRange().map(r -> new ColumnNumber(r.begin.column)),
                    mods));
        }

        private String qualifiedName(Deque<String> stack, String simpleName) {
            if (stack.isEmpty()) return packagePrefix + simpleName;
            var parts = new ArrayList<String>();
            stack.descendingIterator().forEachRemaining(parts::add);
            parts.add(simpleName);
            return packagePrefix + String.join(".", parts);
        }

        private static String buildSignature(String name, NodeList<Parameter> params) {
            return name + "(" + params.stream()
                    .map(p -> p.getType().asString())
                    .collect(Collectors.joining(", ")) + ")";
        }

        private static List<String> modifiers(NodeList<com.github.javaparser.ast.Modifier> mods) {
            return mods.stream()
                    .map(m -> m.getKeyword().asString())
                    .collect(Collectors.toList());
        }
    }

    // ── Reference visitor ─────────────────────────────────────────────────────

    private static final class ReferenceVisitor extends VoidVisitorAdapter<Void> {

        private final FileIdentifier fileId;
        private final List<PendingReference> references;

        ReferenceVisitor(FileIdentifier fileId, List<PendingReference> references) {
            this.fileId = fileId;
            this.references = references;
        }

        @Override
        public void visit(MethodCallExpr n, Void arg) {
            addRef(ReferenceKind.METHOD_CALL, n,
                    () -> { var r = n.resolve(); return r.declaringType().getQualifiedName() + "." + r.getName(); },
                    n.getNameAsString());
            super.visit(n, arg);
        }

        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            addRef(ReferenceKind.CONSTRUCTOR_CALL, n,
                    () -> {
                        var resolved = n.getType().resolve();
                        return resolved.isReferenceType()
                                ? resolved.asReferenceType().getQualifiedName()
                                : resolved.describe();
                    },
                    n.getType().getNameAsString());
            super.visit(n, arg);
        }

        @Override
        public void visit(ClassOrInterfaceType n, Void arg) {
            // Skip the direct type of an ObjectCreationExpr — already recorded as CONSTRUCTOR_CALL
            boolean parentIsNewExpr = n.getParentNode()
                    .filter(p -> p instanceof ObjectCreationExpr)
                    .isPresent();
            if (!parentIsNewExpr) {
                addRef(kindForType(n), n,
                        () -> {
                            var resolved = n.resolve();
                            return resolved.isReferenceType()
                                    ? resolved.asReferenceType().getQualifiedName()
                                    : resolved.describe();
                        },
                        n.getNameAsString());
            }
            super.visit(n, arg);
        }

        @Override
        public void visit(MarkerAnnotationExpr n, Void arg) {
            addRef(ReferenceKind.ANNOTATION_USE, n,
                    () -> n.resolve().getQualifiedName(),
                    n.getNameAsString());
            super.visit(n, arg);
        }

        @Override
        public void visit(NormalAnnotationExpr n, Void arg) {
            addRef(ReferenceKind.ANNOTATION_USE, n,
                    () -> n.resolve().getQualifiedName(),
                    n.getNameAsString());
            super.visit(n, arg);
        }

        @Override
        public void visit(SingleMemberAnnotationExpr n, Void arg) {
            addRef(ReferenceKind.ANNOTATION_USE, n,
                    () -> n.resolve().getQualifiedName(),
                    n.getNameAsString());
            super.visit(n, arg);
        }

        private void addRef(ReferenceKind kind, com.github.javaparser.ast.Node node,
                            ResolveSupplier resolve, String fallback) {
            Optional<LineNumber> line = node.getRange().map(r -> new LineNumber(r.begin.line));
            Optional<ColumnNumber> col = node.getRange().map(r -> new ColumnNumber(r.begin.column));
            try {
                String qn = resolve.get();
                if (qn == null || qn.isBlank()) throw new IllegalStateException("blank qualified name");
                references.add(new PendingReference(
                        Optional.of(new QualifiedName(qn)), Optional.empty(), kind, line, col));
            } catch (Exception e) {
                if (!fallback.isBlank()) {
                    references.add(new PendingReference(
                            Optional.empty(), Optional.of(new SimpleName(fallback)), kind, line, col));
                }
            }
        }

        private static ReferenceKind kindForType(ClassOrInterfaceType n) {
            return n.getParentNode().map(parent -> {
                if (parent instanceof ClassOrInterfaceDeclaration decl) {
                    if (decl.getExtendedTypes().stream().anyMatch(t -> t == n)) return ReferenceKind.EXTENDS;
                    if (decl.getImplementedTypes().stream().anyMatch(t -> t == n)) return ReferenceKind.IMPLEMENTS;
                }
                if (parent instanceof MethodDeclaration md
                        && md.getThrownExceptions().stream().anyMatch(t -> t == n)) return ReferenceKind.THROWS;
                if (parent instanceof ConstructorDeclaration cd
                        && cd.getThrownExceptions().stream().anyMatch(t -> t == n)) return ReferenceKind.THROWS;
                return ReferenceKind.TYPE_USE;
            }).orElse(ReferenceKind.TYPE_USE);
        }

        @FunctionalInterface
        private interface ResolveSupplier {
            String get() throws Exception;
        }
    }
}
