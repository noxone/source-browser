lexer grammar CssLexer;

// ── Comments ──────────────────────────────────────────────────────────────────
BlockComment : '/*' .*? '*/';

// ── Strings ───────────────────────────────────────────────────────────────────
StringDouble : '"' (~["\\\r\n] | '\\' .)* '"';
StringSingle : '\'' (~['\\\r\n] | '\\' .)* '\'';

// ── Numbers with optional CSS units ──────────────────────────────────────────
// Floats before integers so that '1.5em' is a float, not integer + '.5em'.
FloatWithUnit   : [0-9]* '.' [0-9]+ [a-zA-Z%]*;
IntegerWithUnit : [0-9]+ [a-zA-Z%]*;

// ── At-rules (keywords in CSS) ────────────────────────────────────────────────
// Listed explicitly so they receive KEYWORD highlighting.
AtRule
    : '@charset'
    | '@color-profile'
    | '@counter-style'
    | '@font-face'
    | '@font-feature-values'
    | '@import'
    | '@keyframes'
    | '@layer'
    | '@media'
    | '@namespace'
    | '@page'
    | '@property'
    | '@supports'
    ;

// Unknown @-rule — still highlight as keyword.
AtKeyword : '@' [a-zA-Z_-] [a-zA-Z0-9_-]*;

// ── Color values (#rrggbb, #rgb, #rrggbbaa, #rgba) ───────────────────────────
// Must come before Identifier so '#fff' is not split into '#' + 'fff'.
HashColor : '#' [0-9a-fA-F]+;

// ── ID selectors (#foo) ───────────────────────────────────────────────────────
HashIdentifier : '#' [a-zA-Z_-] [a-zA-Z0-9_-]*;

// ── Class selectors ───────────────────────────────────────────────────────────
ClassSelector : '.' [a-zA-Z_-] [a-zA-Z0-9_-]*;

// ── General identifiers (property names, tag names, values) ──────────────────
Identifier : '-'? '--'? [a-zA-Z_] [a-zA-Z0-9_-]*;

// ── Operators / combinators ───────────────────────────────────────────────────
Operator : '>' | '~' | '+' | '||' | '*=' | '~=' | '|=' | '^=' | '$=' | '|' | '*';

// ── Separators ────────────────────────────────────────────────────────────────
Separator : [{}();:,![\]\\];

// ── Whitespace ────────────────────────────────────────────────────────────────
Whitespace : [ \t\r\n]+;

// ── Catch-all ─────────────────────────────────────────────────────────────────
Other : .;
