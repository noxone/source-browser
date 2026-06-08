package com.hlag.sourceviewer.application.scan;

import com.hlag.sourceviewer.domain.model.identifier.FilePath;

import java.util.List;

@FunctionalInterface
interface BatchAction {
    int run(List<FilePath> batch);
}