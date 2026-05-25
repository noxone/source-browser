# Adding a New Language Indexer

This guide explains how to add syntax highlighting and optional symbol recognition for a
new programming language using the ANTLR4-based indexer framework.

## How it works

```
source file
    │
    ▼
ANTLR4 Lexer  (generated from a .g4 grammar you write)
    │ token stream
    ▼
AbstractAntlr4Indexer.indexFile()
    │  maps each token to ExtractedToken.TokenKind
    │  calls extractSymbols() for optional symbol extraction
    ▼
JavaFileParser.ParsedFile
    │  tokens  → stored as compressed token stream → frontend highlights
    │  symbols → stored as Symbol entities → sidebar and search
    ▼
Browser UI
```

The registry (`LanguageIndexerRegistry`) auto-discovers every `@ApplicationScoped` bean
that implements `LanguageIndexer` via CDI injection. No manual registration is needed.

---

## Step 1 — Write the ANTLR4 lexer grammar

Create a file in:

```
src/main/antlr4/com/hlag/sourceviewer/application/scan/antlr/<Language>Lexer.g4
```

The directory path determines the Java package of the generated lexer class
(`com.hlag.sourceviewer.application.scan.antlr`).

### Grammar skeleton

```antlr
lexer grammar <Language>Lexer;

// Comments
BlockComment : '/*' .*? '*/';
LineComment  : '//' ~[\r\n]*;

// String literals
StringDouble : '"' (~["\\\r\n] | '\\' .)* '"';
StringSingle : '\'' (~['\\\r\n] | '\\' .)* '\'';

// Numeric literals
FloatLiteral   : [0-9]* '.' [0-9]+;
IntegerLiteral : [0-9]+;

// Keywords — must appear before Identifier so they take priority
Keyword : 'if' | 'else' | 'while' | /* … */ ;

// Identifiers
Identifier : [a-zA-Z_] [a-zA-Z0-9_]*;

// Operators (longer alternatives first)
Operator : '+=' | '+' | '-=' | '-' | /* … */ ;

// Separators
Separator : [(){}\[\];,.];

// Whitespace
Whitespace : [ \t\r\n]+;

// Catch-all — every unrecognised character becomes an OTHER token
Other : .;
```

### Key rules

| Rule | Purpose |
|------|---------|
| Grammar order | Longer or more specific rules must appear before shorter ones. Keywords before `Identifier`. Longer operators before shorter. |
| Character classes | Inside `[…]`, `]` must be escaped as `\]`; `[` is literal and needs no escaping. |
| `Other : .;` | Always include this catch-all so unrecognised input emits `OTHER` tokens instead of crashing. |
| No hidden channels | Do **not** use `-> channel(HIDDEN)`. The base class uses `Lexer.getAllTokens()` which captures everything; comments must be on the default channel to receive their highlight kind. |

### Build

The ANTLR4 Maven plugin compiles all grammars in `src/main/antlr4` automatically during
`mvn generate-sources`. The generated lexer class is placed under
`target/generated-sources/antlr4/…` and compiled as part of the normal build.

---

## Step 2 — Create the indexer class

```java
package com.hlag.sourceviewer.application.scan.indexer;

import com.hlag.sourceviewer.application.scan.antlr.KotlinLexer; // generated class
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import com.hlag.sourceviewer.domain.model.source.ExtractedToken.TokenKind;
import jakarta.enterprise.context.ApplicationScoped;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;

import static com.hlag.sourceviewer.domain.model.source.ExtractedToken.TokenKind.*;

@ApplicationScoped
public class KotlinIndexer extends AbstractAntlr4Indexer {

    @Override
    public String supportedLanguage() { return "kotlin"; }

    @Override
    public int priority() { return 100; }

    @Override
    public boolean handles(FilePath path) {
        return "kt".equals(path.extension()) || "kts".equals(path.extension());
    }

    @Override
    protected Lexer createLexer(CharStream input) {
        return new KotlinLexer(input);
    }

    @Override
    protected TokenKind mapTokenKind(int tokenType) {
        return switch (tokenType) {
            case KotlinLexer.BlockComment              -> BLOCK_COMMENT;
            case KotlinLexer.LineComment               -> LINE_COMMENT;
            case KotlinLexer.StringLiteral             -> STRING_LITERAL;
            case KotlinLexer.IntegerLiteral            -> INTEGER_LITERAL;
            case KotlinLexer.FloatLiteral              -> FLOAT_LITERAL;
            case KotlinLexer.Keyword                   -> KEYWORD;
            case KotlinLexer.Identifier                -> IDENTIFIER;
            case KotlinLexer.Operator                  -> OPERATOR;
            case KotlinLexer.Separator                 -> SEPARATOR;
            case KotlinLexer.Whitespace                -> WHITESPACE;
            default                                    -> OTHER;
        };
    }
}
```

That is all that is needed for basic syntax highlighting. The CDI container picks up the
`@ApplicationScoped` bean automatically.

---

## Step 3 — Add symbol recognition (optional)

Override `extractSymbols()` to scan the flat token list and emit `Symbol` objects for
declarations the language supports.

```java
@Override
protected List<Symbol> extractSymbols(List<Token> tokens, FilePath path, FileIdentifier fileId) {
    String fileStem = fileStem(path); // "UserService" for "src/UserService.kt"
    List<Token> structural = tokens.stream()
            .filter(t -> t.getType() != KotlinLexer.Whitespace
                      && t.getType() != KotlinLexer.LineComment
                      && t.getType() != KotlinLexer.BlockComment
                      && t.getType() != Token.EOF)
            .toList();

    List<Symbol> symbols = new ArrayList<>();
    for (int i = 0; i < structural.size(); i++) {
        Token tok = structural.get(i);
        // Pattern: 'class' <Identifier>
        if (tok.getType() == KotlinLexer.Keyword && "class".equals(tok.getText())
                && i + 1 < structural.size()) {
            Token nameTok = structural.get(i + 1);
            if (nameTok.getType() == KotlinLexer.Identifier) {
                symbols.add(new Symbol(
                        fileId,
                        SymbolKind.CLASS,
                        new SimpleName(nameTok.getText()),
                        new QualifiedName(fileStem + "." + nameTok.getText()),
                        Optional.empty(),
                        Optional.of(new LineNumber(nameTok.getLine())),
                        Optional.empty(),
                        Optional.of(new ColumnNumber(nameTok.getCharPositionInLine() + 1)),
                        List.of()));
                i++; // skip name token
            }
        }
    }
    return symbols;
}

private static String fileStem(FilePath path) {
    String name = java.nio.file.Path.of(path.value()).getFileName().toString();
    int dot = name.lastIndexOf('.');
    return dot == -1 ? name : name.substring(0, dot);
}
```

### Symbol extraction tips

**Always build a "structural" token list first.** Filter out whitespace, comments, and
catch-all tokens. This makes index arithmetic simpler.

**Track brace depth for class-body methods.** Increment on `{`, decrement on `}`. Record
the depth when a class body opens; method declarations appear at exactly that depth.

**Qualified name convention.** For non-Java languages there is no package system.
Use `<fileStem>.<ClassName>` for classes and `<fileStem>.<ClassName>#<method>` for
methods. This keeps qualified names unique per file.

**Column numbers are 1-based.** ANTLR4's `Token.getCharPositionInLine()` is 0-based;
add 1 before passing to `ColumnNumber`.

**Fail gracefully.** The base class wraps `indexFile()` in a try/catch. If extraction
crashes, the file is skipped with a warning log; no exception propagates to the scan job.

### Available SymbolKind values

| Kind | When to use |
|------|-------------|
| `CLASS` | Class or struct declaration |
| `INTERFACE` | Interface or trait |
| `ENUM` | Enumeration type |
| `RECORD` | Record/data class |
| `ANNOTATION_TYPE` | Annotation or decorator definition |
| `METHOD` | Method, function, or arrow-function declaration |
| `CONSTRUCTOR` | Constructor |
| `FIELD` | Field or property |
| `PARAMETER` | Function parameter (rarely indexed) |
| `LOCAL_VARIABLE` | Local variable (rarely indexed) |

---

## Step 4 — Write tests

Unit tests require no Quarkus context; instantiate the indexer directly.

```java
class KotlinIndexerUnitTest {

    private static final FileIdentifier FILE_ID = new FileIdentifier(1L);
    private KotlinIndexer indexer;

    @BeforeEach
    void setUp() { indexer = new KotlinIndexer(); }

    @Test
    void extracts_class_symbol() {
        var result = indexer.indexFile(FILE_ID, new FilePath("Greeter.kt"),
                "class Greeter { }", null);
        assertThat(result.declarations())
                .anyMatch(s -> s.kind() == SymbolKind.CLASS
                             && "Greeter".equals(s.name().value()));
    }

    @Test
    void keywords_are_highlighted() {
        var result = indexer.indexFile(FILE_ID, new FilePath("a.kt"),
                "class fun val var if", null);
        assertThat(result.tokens())
                .anyMatch(t -> "class".equals(t.text())
                             && t.kind() == TokenKind.KEYWORD);
    }
}
```

Minimum tests to write:
- `handles()` for each supported extension
- `supportedLanguage()` returns the correct token
- `priority()` is 100
- `analyze()` returns true
- spot-check keyword, string, comment, and number token kinds
- symbol extraction (if implemented): at least one class and one method

---

## Token kind reference

| `TokenKind` | Typical visual style | Use for |
|-------------|---------------------|---------|
| `KEYWORD` | Bold blue | Reserved words |
| `IDENTIFIER` | Dark grey | Variable, class, method names (unresolved) |
| `STRING_LITERAL` | Orange | Single- and double-quoted strings |
| `CHAR_LITERAL` | Orange | Single-character literals (e.g. Java `'a'`) |
| `INTEGER_LITERAL` | Purple | Integer constants |
| `LONG_LITERAL` | Purple | Long/BigInt constants |
| `FLOAT_LITERAL` | Purple | Floating-point constants |
| `DOUBLE_LITERAL` | Purple | Double-precision constants |
| `LINE_COMMENT` | Italic green | `// …` |
| `BLOCK_COMMENT` | Italic green | `/* … */` |
| `JAVADOC_COMMENT` | Italic green | `/** … */` (Java only) |
| `OPERATOR` | Light grey | `+`, `-`, `===`, `=>`, … |
| `SEPARATOR` | Lighter grey | `(`, `)`, `{`, `}`, `;`, `,`, … |
| `WHITESPACE` | (invisible) | Spaces, tabs, newlines |
| `OTHER` | Dark grey | Anything not matched above |

---

## Troubleshooting

**Grammar ambiguity warnings.** ANTLR4 prints warnings for ambiguous alternatives but
still generates the lexer. Resolve by reordering rules (more specific first) or using a
longer match.

**Comments not highlighted.** Make sure you are not using `-> channel(HIDDEN)` in the
grammar. The base class calls `Lexer.getAllTokens()`, which returns all tokens. Tokens
on the hidden channel are included, but if you forget to return a `TokenKind` for them in
`mapTokenKind()`, they silently become `OTHER`.

**Column numbers off by one.** ANTLR4 positions are 0-based. Always do
`token.getCharPositionInLine() + 1` when constructing `ColumnNumber`.

**ArchUnit failure.** The ANTLR4 runtime (`org.antlr.v4.runtime.*`) is an external
library and is exempt from layer checks (the rule uses
`consideringOnlyDependenciesInLayers()`). If you accidentally put a new class in a
package outside the defined layers, move it into
`com.hlag.sourceviewer.application.scan.indexer`.

**Symbols not appearing in the sidebar.** Check that the `qualifiedName` is unique across
files. Duplicate qualified names cause silent deduplication in the persistence layer.
