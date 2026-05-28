package com.hlag.sourceviewer.application.scan.lsp;

import com.hlag.sourceviewer.domain.model.repository.Repository;
import java.nio.file.Path;

/** Describes the repository project for which a language server should be started. */
public record LspProjectContext(Repository repository, Path projectRoot) {
}

