package com.hlag.sourceviewer.application.scan.treesitter;

import ch.usi.si.seart.treesitter.Language;
import ch.usi.si.seart.treesitter.Node;
import com.hlag.sourceviewer.application.lsp.LspServerManager;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import com.hlag.sourceviewer.domain.model.source.ExtractedToken.TokenKind;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Set;

import static com.hlag.sourceviewer.domain.model.source.ExtractedToken.TokenKind.*;

/**
 * Indexes {@code .java} files using TreeSitter for tokenisation and
 * Eclipse JDT Language Server for symbol extraction.
 *
 * <p>TreeSitter grammars are bundled in the {@code java-tree-sitter} library;
 * no external grammar files are needed.</p>
 */
@ApplicationScoped
public class JavaTreeSitterLspIndexer extends AbstractTreeSitterLspIndexer {

    private static final Set<String> JAVA_KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "final", "finally", "float", "for", "goto", "if", "implements",
            "import", "instanceof", "int", "interface", "long", "native", "new",
            "package", "private", "protected", "public", "record", "return", "sealed",
            "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "var", "void", "volatile", "while",
            "yield", "permits", "non-sealed", "true", "false", "null"
    );

    protected JavaTreeSitterLspIndexer() {
        super(null);
    }

    @Inject
    public JavaTreeSitterLspIndexer(LspServerManager lspServerManager) {
        super(lspServerManager);
    }

    @Override
    public String supportedLanguage() {
        return "java";
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public boolean handles(FilePath path) {
        return "java".equals(path.extension());
    }

    @Override
    protected Language loadLanguage() {
        return Language.JAVA;
    }

    @Override
    protected TokenKind mapNodeKind(Node node) {
        String type = node.getType();

        if (node.isNamed()) {
            return switch (type) {
                case "line_comment"                      -> LINE_COMMENT;
                case "block_comment"                     -> BLOCK_COMMENT;
                case "string_literal", "string_fragment",
                     "text_block"                        -> STRING_LITERAL;
                case "character_literal"                 -> CHAR_LITERAL;
                case "decimal_integer_literal",
                     "hex_integer_literal",
                     "binary_integer_literal",
                     "octal_integer_literal"             -> INTEGER_LITERAL;
                case "long_literal"                      -> LONG_LITERAL;
                case "decimal_floating_point_literal",
                     "hex_floating_point_literal"        -> FLOAT_LITERAL;
                case "identifier", "type_identifier"     -> IDENTIFIER;
                default                                  -> OTHER;
            };
        }

        if (JAVA_KEYWORDS.contains(type)) {
            return KEYWORD;
        }
        return switch (type) {
            case "+", "-", "*", "/", "%",
                 "=", "+=", "-=", "*=", "/=", "%=",
                 "&=", "|=", "^=", "<<=", ">>=", ">>>=",
                 "==", "!=", "<=", ">=",
                 "&&", "||", "!", "~",
                 "&", "|", "^", "<<", ">>", ">>>",
                 "++", "--", "->", "::", "?", ":",
                 "@", "..."                              -> OPERATOR;
            case "(", ")", "{", "}", "[", "]",
                 ";", ",", "."                           -> SEPARATOR;
            default                                      -> OTHER;
        };
    }
}
