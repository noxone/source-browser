package com.hlag.sourceviewer.domain.model.source.detail;

import java.util.List;

/** Detail for a type declaration (class/interface/enum/record/annotation). */
public record TypeDeclDetail(
        String qualifiedName,
        String kind,
        String superclassFqn,
        List<String> implementedInterfaces) {}
