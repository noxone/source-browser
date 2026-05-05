package com.hlag.sourceviewer.domain.model.search;

import com.hlag.sourceviewer.domain.model.identifier.Description;
import com.hlag.sourceviewer.domain.model.identifier.DisplayName;
import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.FilePath;
import com.hlag.sourceviewer.domain.model.identifier.*;

/**
 * Ein einzelner Treffer aus der Volltext-Suche.
 */
public record SearchResult(
        FileIdentifier fileIdentifier,
        FilePath filePath,
        DisplayName repositoryName,
        Description snippet,
        double relevanceScore
) {}
