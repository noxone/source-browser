package com.hlag.sourceviewer.application.scan.indexer;

import com.hlag.sourceviewer.application.scan.antlr.CssLexer;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import com.hlag.sourceviewer.domain.model.source.ExtractedToken.TokenKind;
import jakarta.enterprise.context.ApplicationScoped;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;

import static com.hlag.sourceviewer.domain.model.source.ExtractedToken.TokenKind.*;

/** Indexes {@code .css} files: tokenises with an ANTLR4 lexer and maps token types to highlight kinds. */
@ApplicationScoped
public class CssIndexer extends AbstractAntlr4Indexer {

    @Override
    public String supportedLanguage() {
        return "css";
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean handles(FilePath path) {
        return "css".equals(path.extension());
    }

    @Override
    protected Lexer createLexer(CharStream input) {
        return new CssLexer(input);
    }

    @Override
    protected TokenKind mapTokenKind(int tokenType) {
        return switch (tokenType) {
            case CssLexer.BlockComment                   -> BLOCK_COMMENT;
            case CssLexer.StringDouble, CssLexer.StringSingle -> STRING_LITERAL;
            case CssLexer.FloatWithUnit                  -> FLOAT_LITERAL;
            case CssLexer.IntegerWithUnit                -> INTEGER_LITERAL;
            case CssLexer.AtRule, CssLexer.AtKeyword     -> KEYWORD;
            case CssLexer.HashColor, CssLexer.HashIdentifier,
                 CssLexer.ClassSelector, CssLexer.Identifier -> IDENTIFIER;
            case CssLexer.Operator                       -> OPERATOR;
            case CssLexer.Separator                      -> SEPARATOR;
            case CssLexer.Whitespace                     -> WHITESPACE;
            default                                      -> OTHER;
        };
    }
}
