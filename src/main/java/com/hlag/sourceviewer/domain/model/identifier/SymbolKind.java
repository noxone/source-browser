package com.hlag.sourceviewer.domain.model.identifier;

/**
 * Art einer Symbol-Deklaration im Quellcode.
 */
public enum SymbolKind {
    CLASS,
    INTERFACE,
    ENUM,
    RECORD,
    ANNOTATION_TYPE,
    METHOD,
    CONSTRUCTOR,
    FIELD,
    PARAMETER,
    LOCAL_VARIABLE,
    /** Top-level function (not a class method). */
    FUNCTION,
    /** Generic variable (e.g. in scripting languages that don't distinguish local/field). */
    VARIABLE,
    /** Class/object property (e.g. TypeScript, Kotlin). */
    PROPERTY,
    /** Namespace or module declaration. */
    NAMESPACE,
    /** Type alias / typedef. */
    TYPE_ALIAS,
    /** Named constant inside an enum (Java enum constant, Rust variant, etc.). */
    ENUM_CONSTANT,
    /** File-level or package-level module. */
    MODULE,
    CONSTANT
}
