package com.hlag.sourceviewer.domain.model.identifier;

/**
 * Art einer Referenz auf ein Symbol.
 */
public enum ReferenceKind {
    TYPE_USE,
    METHOD_CALL,
    FIELD_READ,
    FIELD_WRITE,
    ANNOTATION_USE,
    CONSTRUCTOR_CALL,
    EXTENDS,
    IMPLEMENTS,
    THROWS
}
