package com.hlag.sourceviewer.application.scan.indexer;

import com.hlag.sourceviewer.application.scan.antlr.JavaLexer;
import com.hlag.sourceviewer.domain.model.identifier.ColumnNumber;
import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import com.hlag.sourceviewer.domain.model.identifier.LineNumber;
import com.hlag.sourceviewer.domain.model.identifier.QualifiedName;
import com.hlag.sourceviewer.domain.model.identifier.SimpleName;
import com.hlag.sourceviewer.domain.model.identifier.SymbolKind;
import com.hlag.sourceviewer.domain.model.source.ExtractedToken.TokenKind;
import com.hlag.sourceviewer.domain.model.source.Symbol;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;

import static com.hlag.sourceviewer.domain.model.source.ExtractedToken.TokenKind.*;

/**
 * Java fallback indexer using ANTLR tokenization and lightweight symbol extraction.
 *
 * <p>Priority is intentionally between Maven-aware Java indexing and the generic
 * JavaParser fallback.</p>
 */
@ApplicationScoped
public class JavaAntlrIndexer extends AbstractAntlr4Indexer {

    private static final int CLASS_BODY_UNKNOWN = -1;

    private static final Set<String> TYPE_KEYWORDS = Set.of("class", "interface", "enum", "record");
    private static final Set<String> MEMBER_MODIFIERS = Set.of(
            "public", "private", "protected", "static", "final", "abstract", "native", "synchronized",
            "strictfp", "default", "transient", "volatile");

    @Override
    public String supportedLanguage() {
        return "java";
    }

    @Override
    public int priority() {
        return 50;
    }

    @Override
    public boolean handles(FilePath path) {
        return path.isJavaFile();
    }

    @Override
    protected Lexer createLexer(CharStream input) {
        return new JavaLexer(input);
    }

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
