package com.hlag.sourceviewer.domain.model.source.detail;

/** Detail for a variable reference or declaration (local variable, parameter, or field). */
public record VariableDetail(
        String name,
        /** LOCAL, PARAMETER, or FIELD */
        String variableKind,
        String typeFqn) {}
