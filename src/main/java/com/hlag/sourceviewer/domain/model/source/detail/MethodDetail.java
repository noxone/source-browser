package com.hlag.sourceviewer.domain.model.source.detail;

import java.util.List;

/** Detail for a method call or method declaration. */
public record MethodDetail(
        String name,
        String declaringClass,
        String returnType,
        List<MethodParam> parameters,
        boolean isConstructor) {

    public record MethodParam(String name, String type) {}
}
