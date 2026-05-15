lexer grammar TypeScriptLexer;

// ── Comments ──────────────────────────────────────────────────────────────────
BlockComment : '/*' .*? '*/';
LineComment  : '//' ~[\r\n]*;

// ── Template literals ─────────────────────────────────────────────────────────
// Simplified: nested ${...} expressions are matched non-greedily.
// This covers the common case; deeply nested or multi-level templates
// are approximated as a single token.
TemplateLiteral : '`' (~[`\\] | '\\' . | '${' .*? '}')* '`';

// ── String literals ───────────────────────────────────────────────────────────
StringDouble : '"' (~["\\\r\n] | '\\' .)* '"';
StringSingle : '\'' (~['\\\r\n] | '\\' .)* '\'';

// ── Numeric literals ──────────────────────────────────────────────────────────
BigIntLiteral   : [0-9]+ 'n';
HexLiteral      : '0' [xX] [0-9a-fA-F]+;
OctalLiteral    : '0' [oO] [0-7]+;
BinaryLiteral   : '0' [bB] [01]+;
FloatLiteral    : [0-9]* '.' [0-9]+ ([eE] [+\-]? [0-9]+)?
                | [0-9]+ ([eE] [+\-]? [0-9]+)
                ;
DecimalLiteral  : [0-9]+;

// ── Keywords ──────────────────────────────────────────────────────────────────
// Declared before Identifier so the lexer prefers an exact keyword match.
// TypeScript 5 + ES2022 reserved words and contextual keywords.
KwAbstract    : 'abstract';
KwAny         : 'any';
KwAs          : 'as';
KwAsserts     : 'asserts';
KwAsync       : 'async';
KwAwait       : 'await';
KwBoolean     : 'boolean';
KwBreak       : 'break';
KwCase        : 'case';
KwCatch       : 'catch';
KwClass       : 'class';
KwConst       : 'const';
KwConstructor : 'constructor';
KwContinue    : 'continue';
KwDebugger    : 'debugger';
KwDeclare     : 'declare';
KwDefault     : 'default';
KwDelete      : 'delete';
KwDo          : 'do';
KwElse        : 'else';
KwEnum        : 'enum';
KwExport      : 'export';
KwExtends     : 'extends';
KwFalse       : 'false';
KwFinally     : 'finally';
KwFor         : 'for';
KwFrom        : 'from';
KwFunction    : 'function';
KwGet         : 'get';
KwGlobal      : 'global';
KwIf          : 'if';
KwImplements  : 'implements';
KwImport      : 'import';
KwIn          : 'in';
KwInfer       : 'infer';
KwInstanceof  : 'instanceof';
KwInterface   : 'interface';
KwIs          : 'is';
KwKeyof       : 'keyof';
KwLet         : 'let';
KwModule      : 'module';
KwNamespace   : 'namespace';
KwNever       : 'never';
KwNew         : 'new';
KwNull        : 'null';
KwNumber      : 'number';
KwObject      : 'object';
KwOf          : 'of';
KwOverride    : 'override';
KwPackage     : 'package';
KwPrivate     : 'private';
KwProtected   : 'protected';
KwPublic      : 'public';
KwReadonly    : 'readonly';
KwReturn      : 'return';
KwSatisfies   : 'satisfies';
KwSet         : 'set';
KwStatic      : 'static';
KwString      : 'string';
KwSuper       : 'super';
KwSwitch      : 'switch';
KwSymbol      : 'symbol';
KwThis        : 'this';
KwThrow       : 'throw';
KwTrue        : 'true';
KwTry         : 'try';
KwType        : 'type';
KwTypeof      : 'typeof';
KwUndefined   : 'undefined';
KwUnique      : 'unique';
KwUnknown     : 'unknown';
KwVar         : 'var';
KwVoid        : 'void';
KwWhile       : 'while';
KwWith        : 'with';
KwYield       : 'yield';

// ── Identifiers ───────────────────────────────────────────────────────────────
Identifier : [$_a-zA-Z-￾] [$_a-zA-Z0-9-￾]*;

// ── Operators ─────────────────────────────────────────────────────────────────
// Longer alternatives must appear before shorter ones.
Operator
    : '===' | '!==' | '==' | '!='
    | '>>>' | '>>' | '<<'
    | '>=' | '<='
    | '**' | '++' | '--'
    | '&&' | '||' | '??'
    | '=>'
    | '...'
    | '?.'
    | '**=' | '>>>=' | '>>=' | '<<='
    | '&&=' | '||=' | '??='
    | '+=' | '-=' | '*=' | '/=' | '%='
    | '&=' | '|=' | '^='
    | '>' | '<'
    | '+' | '-' | '*' | '/' | '%'
    | '!' | '~'
    | '&' | '|' | '^'
    | '='
    | '?'  | ':'
    ;

// ── Separators ────────────────────────────────────────────────────────────────
Separator : [(){}[\];,.'@#\\];

// ── Whitespace ────────────────────────────────────────────────────────────────
Whitespace : [ \t\r\n]+;

// ── Catch-all ─────────────────────────────────────────────────────────────────
Other : .;
