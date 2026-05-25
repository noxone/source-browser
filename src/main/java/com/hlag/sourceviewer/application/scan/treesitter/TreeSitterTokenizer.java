package com.hlag.sourceviewer.application.scan.treesitter;

import ch.usi.si.seart.treesitter.Language;
import ch.usi.si.seart.treesitter.Node;
import ch.usi.si.seart.treesitter.Parser;
import ch.usi.si.seart.treesitter.Point;
import ch.usi.si.seart.treesitter.Tree;
import ch.usi.si.seart.treesitter.exception.parser.ParsingException;
import com.hlag.sourceviewer.domain.model.source.ExtractedToken;
import com.hlag.sourceviewer.domain.model.source.ExtractedToken.TokenKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Utility that converts a tree-sitter parse tree into the domain's
 * {@link ExtractedToken} list.
 *
 * <p>Traverses every leaf node of the tree, maps its node type to a
 * {@link TokenKind} via the supplied mapper, and fills gaps between adjacent
 * leaves with {@code WHITESPACE} tokens.  Positions are converted from the
 * 0-based tree-sitter convention to the 1-based domain convention.</p>
 */
class TreeSitterTokenizer {

    private static final Logger logger = LoggerFactory.getLogger(TreeSitterTokenizer.class);

    private TreeSitterTokenizer() {}

    /**
     * Parses {@code content} with the given language and returns the token list.
     *
     * @param language    tree-sitter grammar
     * @param content     source file text (UTF-8 string)
     * @param kindMapper  maps a tree-sitter node to a {@link TokenKind}
     */
    static List<ExtractedToken> tokenize(Language language, String content,
                                         Function<Node, TokenKind> kindMapper) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }
        try (Parser parser = Parser.getFor(language);
             Tree tree = parser.parse(content)) {
            Node root = tree.getRootNode();
            List<Node> leaves = new ArrayList<>();
            collectLeaves(root, leaves);
            return buildTokens(content, leaves, kindMapper);
        } catch (ParsingException e) {
            logger.warn("TreeSitter parsing failed: {}", e.getMessage());
            return List.of();
        } catch (Exception e) {
            logger.warn("TreeSitter tokenization failed: {}", e.getMessage());
            return List.of();
        }
    }

    private static void collectLeaves(Node node, List<Node> result) {
        int childCount = node.getChildCount();
        if (childCount == 0) {
            result.add(node);
        } else {
            for (int i = 0; i < childCount; i++) {
                collectLeaves(node.getChild(i), result);
            }
        }
    }

    private static List<ExtractedToken> buildTokens(String content, List<Node> leaves,
                                                     Function<Node, TokenKind> kindMapper) {
        List<ExtractedToken> result = new ArrayList<>(leaves.size() * 2);
        int prevEndByte = 0;

        for (Node leaf : leaves) {
            int startByte = leaf.getStartByte();
            int endByte = leaf.getEndByte();

            if (startByte < 0 || endByte < 0 || startByte > endByte || endByte > content.length()) {
                continue;
            }

            // Fill gap between previous leaf end and this leaf start with whitespace
            if (startByte > prevEndByte) {
                String gap = content.substring(prevEndByte, startByte);
                if (!gap.isEmpty()) {
                    addGapToken(result, gap, prevEndByte, content);
                }
            }

            String text = content.substring(startByte, endByte);
            if (!text.isEmpty()) {
                Point start = leaf.getStartPoint();
                Point end = leaf.getEndPoint();
                result.add(new ExtractedToken(
                        start.getRow() + 1,       // 0-based → 1-based
                        start.getColumn() + 1,
                        end.getColumn(),           // columnEnd is exclusive, stays as-is
                        text,
                        kindMapper.apply(leaf),
                        null,
                        null));
            }
            prevEndByte = endByte;
        }

        // Trailing whitespace after last leaf
        if (prevEndByte < content.length()) {
            String gap = content.substring(prevEndByte);
            if (!gap.isEmpty()) {
                addGapToken(result, gap, prevEndByte, content);
            }
        }

        return result;
    }

    private static void addGapToken(List<ExtractedToken> result, String gap, int gapStartByte,
                                    String content) {
        // Calculate line/column of the gap start by counting preceding newlines
        int line = 1;
        int col = 1;
        for (int i = 0; i < gapStartByte && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
        }
        result.add(new ExtractedToken(line, col, col + gap.length() - 1,
                gap, TokenKind.WHITESPACE, null, null));
    }
}
