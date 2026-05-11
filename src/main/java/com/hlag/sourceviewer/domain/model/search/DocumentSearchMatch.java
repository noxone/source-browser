package com.hlag.sourceviewer.domain.model.search;

import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;

public record DocumentSearchMatch(
        FileIdentifier fileIdentifier,
        String snippet,
        double rank
) {}
