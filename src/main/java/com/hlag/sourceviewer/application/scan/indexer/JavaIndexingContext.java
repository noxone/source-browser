package com.hlag.sourceviewer.application.scan.indexer;

import com.github.javaparser.resolution.TypeSolver;

/** Per-scan context for Java-based language indexers. */
public record JavaIndexingContext(TypeSolver typeSolver) {}
