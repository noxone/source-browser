package com.hlag.sourceviewer.application.scan.indexer;

import com.hlag.sourceviewer.application.scan.antlr.TypeScriptLexer;
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
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.hlag.sourceviewer.domain.model.source.ExtractedToken.TokenKind.*;

/**
 * Indexes {@code .ts} and {@code .tsx} files: tokenises with an ANTLR4 lexer and provides
 * basic symbol extraction for top-level class declarations, top-level function declarations,
 * and method declarations inside class bodies.
 */
@ApplicationScoped
public class TypeScriptIndexer extends AbstractAntlr4Indexer {

    // Token types for the access-modifier and method-qualifier keywords.
    private static final Set<Integer> METHOD_MODIFIERS = Set.of(
            TypeScriptLexer.KwPublic, TypeScriptLexer.KwPrivate, TypeScriptLexer.KwProtected,
            TypeScriptLexer.KwStatic, TypeScriptLexer.KwAsync, TypeScriptLexer.KwAbstract,
            TypeScriptLexer.KwOverride, TypeScriptLexer.KwReadonly,
            TypeScriptLexer.KwGet, TypeScriptLexer.KwSet
    );

    @Override
    public String supportedLanguage() {
        return "typescript";
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean handles(FilePath path) {
        String ext = path.extension();
        return "ts".equals(ext) || "tsx".equals(ext);
    }

    @Override
    protected Lexer createLexer(CharStream input) {
        return new TypeScriptLexer(input);
    }

    @Override
    protected TokenKind mapTokenKind(int tokenType) {
        if (tokenType >= TypeScriptLexer.KwAbstract && tokenType <= TypeScriptLexer.KwYield) {
            return KEYWORD;
        }
        return switch (tokenType) {
            case TypeScriptLexer.BlockComment              -> BLOCK_COMMENT;
            case TypeScriptLexer.LineComment               -> LINE_COMMENT;
            case TypeScriptLexer.TemplateLiteral,
                 TypeScriptLexer.StringDouble,
                 TypeScriptLexer.StringSingle              -> STRING_LITERAL;
            case TypeScriptLexer.BigIntLiteral             -> LONG_LITERAL;
            case TypeScriptLexer.HexLiteral,
                 TypeScriptLexer.OctalLiteral,
                 TypeScriptLexer.BinaryLiteral,
                 TypeScriptLexer.DecimalLiteral            -> INTEGER_LITERAL;
            case TypeScriptLexer.FloatLiteral              -> FLOAT_LITERAL;
            case TypeScriptLexer.Identifier                -> IDENTIFIER;
            case TypeScriptLexer.Operator                  -> OPERATOR;
            case TypeScriptLexer.Separator                 -> SEPARATOR;
            case TypeScriptLexer.Whitespace                -> WHITESPACE;
            default                                        -> OTHER;
        };
    }

    /**
     * Extracts class and method/function declarations by scanning the flat token list.
     *
     * <p>Recognised patterns:</p>
     * <ul>
     *   <li>{@code class <Identifier>} → {@link SymbolKind#CLASS}</li>
     *   <li>{@code function <Identifier>} at brace depth 0 → {@link SymbolKind#METHOD}</li>
     *   <li>{@code [modifier*] <Identifier|constructor> (} inside a class body → {@link SymbolKind#METHOD}</li>
     * </ul>
     */
    @Override
    protected List<Symbol> extractSymbols(List<Token> tokens, FilePath path, FileIdentifier fileId) {
        String fileStem = fileStem(path);
        List<Token> structural = tokens.stream()
                .filter(t -> t.getType() != TypeScriptLexer.Whitespace
                          && t.getType() != TypeScriptLexer.LineComment
                          && t.getType() != TypeScriptLexer.BlockComment
                          && t.getType() != TypeScriptLexer.Other
                          && t.getType() != Token.EOF)
                .toList();

        List<Symbol> symbols = new ArrayList<>();
        int braceDepth = 0;
        int classBodyDepth = -1;   // brace depth of the current class body (-1 = not inside a class)
        String currentClass = null;
        boolean pendingClassOpen = false; // waiting for the '{' that opens the class body

        for (int i = 0; i < structural.size(); i++) {
            Token tok = structural.get(i);

            if (isSeparator(tok, "{")) {
                braceDepth++;
                if (pendingClassOpen) {
                    classBodyDepth = braceDepth;
                    pendingClassOpen = false;
                }
                continue;
            }
            if (isSeparator(tok, "}")) {
                if (braceDepth == classBodyDepth) {
                    classBodyDepth = -1;
                    currentClass = null;
                }
                braceDepth--;
                continue;
            }

            // ── class <Identifier> ────────────────────────────────────────────
            if (tok.getType() == TypeScriptLexer.KwClass) {
                Token nameTok = lookAhead(structural, i + 1);
                if (nameTok != null && nameTok.getType() == TypeScriptLexer.Identifier) {
                    currentClass = nameTok.getText();
                    symbols.add(buildSymbol(fileId, SymbolKind.CLASS, nameTok,
                            fileStem + "." + currentClass));
                    pendingClassOpen = true;
                    i++; // skip the name token in the main loop
                }
                continue;
            }

            // ── function <Identifier>  (top level only) ───────────────────────
            if (tok.getType() == TypeScriptLexer.KwFunction && braceDepth == 0) {
                Token nameTok = lookAhead(structural, i + 1);
                if (nameTok != null && nameTok.getType() == TypeScriptLexer.Identifier) {
                    symbols.add(buildSymbol(fileId, SymbolKind.METHOD, nameTok,
                            fileStem + "." + nameTok.getText()));
                    i++;
                }
                continue;
            }

            // ── Method declarations inside a class body ───────────────────────
            if (classBodyDepth >= 0 && braceDepth == classBodyDepth) {
                // Skip over any leading modifier keywords (public, static, async, …)
                int j = i;
                while (j < structural.size() && METHOD_MODIFIERS.contains(structural.get(j).getType())) {
                    j++;
                }
                if (j >= structural.size()) {
                    continue;
                }
                Token candidate = structural.get(j);
                boolean isMethodName = candidate.getType() == TypeScriptLexer.Identifier
                        || candidate.getType() == TypeScriptLexer.KwConstructor;
                if (isMethodName) {
                    Token next = lookAhead(structural, j + 1);
                    if (next != null && isSeparator(next, "(")) {
                        String qualifiedName = currentClass != null
                                ? fileStem + "." + currentClass + "#" + candidate.getText()
                                : fileStem + "." + candidate.getText();
                        symbols.add(buildSymbol(fileId, SymbolKind.METHOD, candidate, qualifiedName));
                        i = j; // fast-forward past the modifiers and name
                    }
                }
            }
        }
        return symbols;
    }

    private Symbol buildSymbol(FileIdentifier fileId, SymbolKind kind, Token nameTok, String qualifiedName) {
        int col = nameTok.getCharPositionInLine() + 1; // ANTLR4 is 0-based
        return new Symbol(
                fileId,
                kind,
                new SimpleName(nameTok.getText()),
                new QualifiedName(qualifiedName),
                Optional.empty(),
                Optional.of(new LineNumber(nameTok.getLine())),
                Optional.empty(),
                Optional.of(new ColumnNumber(col)),
                List.of());
    }

    private static boolean isSeparator(Token tok, String text) {
        return tok.getType() == TypeScriptLexer.Separator && text.equals(tok.getText());
    }

    private static Token lookAhead(List<Token> tokens, int index) {
        return index < tokens.size() ? tokens.get(index) : null;
    }

    private static String fileStem(FilePath path) {
        String name = Path.of(path.value()).getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot == -1 ? name : name.substring(0, dot);
    }
}
