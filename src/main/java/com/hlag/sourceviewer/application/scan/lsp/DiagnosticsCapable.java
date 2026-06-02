package com.hlag.sourceviewer.application.scan.lsp;

import java.util.concurrent.TimeUnit;
import org.eclipse.lsp4j.services.LanguageClient;

/** Language client that can track per-file diagnostic availability. */
public interface DiagnosticsCapable extends LanguageClient {

    /** Registers a latch so that {@link #awaitDiagnostics} can block until diagnostics arrive. */
    void waitForDiagnostics(String uri);

    /** Blocks until diagnostics for {@code uri} are received or the timeout elapses. */
    boolean awaitDiagnostics(String uri, long timeout, TimeUnit unit);
}
