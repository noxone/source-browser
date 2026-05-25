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
 * Indexes {@code .ts} and {@code .tsx} files using TreeSitter for tokenisation
 * and {@code typescript-language-server} for symbol extraction.
 *
 * <p>TreeSitter grammars are bundled in the {@code java-tree-sitter} library;
 * no external grammar files are needed.</p>
 */
@ApplicationScoped
public class TypeScriptTreeSitterLspIndexer extends AbstractTreeSitterLspIndexer {

    private static final Set<String> TS_KEYWORDS = Set.of(
            "abstract", "as", "async", "await", "break", "case", "catch", "class",
            "const", "constructor", "continue", "debugger", "declare", "default",
            "delete", "do", "else", "enum", "export", "extends", "false", "finally",
            "for", "from", "function", "get", "if", "implements", "import", "in",
            "infer", "instanceof", "interface", "is", "keyof", "let", "module",
            "namespace", "never", "new", "null", "of", "override", "package", "private",
            "protected", "public", "readonly", "require", "return", "satisfies", "set",
            "static", "super", "switch", "symbol", "this", "throw", "true", "try",
            "type", "typeof", "undefined", "unique", "var", "void", "while", "with",
            "yield"
    );

    protected TypeScriptTreeSitterLspIndexer() {
        super(null);
    }

    @Inject
    public TypeScriptTreeSitterLspIndexer(LspServerManager lspServerManager) {
        super(lspServerManager);
    }

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
    protected Language loadLanguage() {
        return Language.TYPESCRIPT;
    }

    @Override
    protected TokenKind mapNodeKind(Node node) {
        String type = node.getType();

        if (node.isNamed()) {
            return switch (type) {
                case "comment"                           -> LINE_COMMENT;
                case "hash_bang_line"                    -> LINE_COMMENT;
                case "string", "template_string",
                     "template_literal_type"             -> STRING_LITERAL;
                case "number"                            -> FLOAT_LITERAL;
                case "regex"                             -> STRING_LITERAL;
                case "identifier", "type_identifier",
                     "property_identifier",
                     "shorthand_property_identifier",
                     "this", "super"                     -> IDENTIFIER;
                default                                  -> OTHER;
            };
        }

        if (TS_KEYWORDS.contains(type)) {
            return KEYWORD;
        }
        return switch (type) {
            case "+", "-", "*", "/", "%",
                 "=", "+=", "-=", "*=", "/=", "%=",
                 "**=", "&&=", "||=", "??=",
                 "==", "===", "!=", "!==",
                 "<=", ">=",
                 "&&", "||", "??", "!",
                 "&", "|", "^", "~",
                 "<<", ">>", ">>>",
                 "++", "--", "=>",
                 "?", ":", "?.", "...",
                 "@"                                     -> OPERATOR;
            case "(", ")", "{", "}", "[", "]",
                 ";", ",", "."                           -> SEPARATOR;
            default                                      -> OTHER;
        };
    }
}
