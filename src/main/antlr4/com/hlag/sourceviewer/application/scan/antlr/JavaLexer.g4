lexer grammar JavaLexer;

// Comments
JavadocComment : '/**' .*? '*/';
BlockComment   : '/*' .*? '*/';
LineComment    : '//' ~[\r\n]*;

// String and char literals
TextBlock    : '"""' .*? '"""';
StringDouble : '"' (~["\\\r\n] | '\\' .)* '"';
CharLiteral  : '\'' (~['\\\r\n] | '\\' .) '\'';

// Numeric literals
HexLiteral      : '0' [xX] [0-9a-fA-F] [0-9a-fA-F_]* [lL]?;
BinaryLiteral   : '0' [bB] [01] [01_]* [lL]?;
FloatLiteral    : [0-9] [0-9_]* '.' [0-9] [0-9_]* ([eE] [+-]? [0-9] [0-9_]*)? [fFdD]?
                | [0-9] [0-9_]* [eE] [+-]? [0-9] [0-9_]* [fFdD]?
                | [0-9] [0-9_]* [fFdD]
                ;
LongLiteral     : [0-9] [0-9_]* [lL];
IntegerLiteral  : [0-9] [0-9_]*;

// Keywords (Java 21 + literals treated as keywords for highlighting)
Keyword
    : 'abstract' | 'assert' | 'boolean' | 'break' | 'byte' | 'case' | 'catch'
    | 'char' | 'class' | 'const' | 'continue' | 'default' | 'do' | 'double'
    | 'else' | 'enum' | 'extends' | 'final' | 'finally' | 'float' | 'for'
    | 'if' | 'goto' | 'implements' | 'import' | 'instanceof' | 'int'
    | 'interface' | 'long' | 'native' | 'new' | 'package' | 'private'
    | 'protected' | 'public' | 'return' | 'short' | 'static' | 'strictfp'
    | 'super' | 'switch' | 'synchronized' | 'this' | 'throw' | 'throws'
    | 'transient' | 'try' | 'void' | 'volatile' | 'while'
    | 'record' | 'sealed' | 'permits' | 'non-sealed' | 'var' | 'yield'
    | 'true' | 'false' | 'null'
    ;

// Identifiers
Identifier : [$_a-zA-Z] [$_a-zA-Z0-9]*;

// Operators (longer alternatives first)
Operator
    : '>>>=' | '>>=' | '<<='
    | '++' | '--' | '&&' | '||' | '==' | '!=' | '<=' | '>='
    | '->' | '::' | '<<' | '>>' | '>>>'
    | '+=' | '-=' | '*=' | '/=' | '%=' | '&=' | '^=' | '|='
    | '=' | '>' | '<' | '!'
    | '~' | '?' | ':'
    | '+' | '-' | '*' | '/' | '%' | '&' | '|' | '^'
    ;

// Separators
Separator : [(){}[\];,.@];

// Whitespace
Whitespace : [ \t\r\n]+;

// Catch-all
Other : .;
