package com.hlag.sourceviewer.domain.model.source.detail;

/** Detail for a reference to a class, interface, enum, record, or annotation type. */
public record TypeRefDetail(String qualifiedName, String kind) {}
