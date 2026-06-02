package com.hlag.sourceviewer.infrastructure.lsp.jdtls;

import com.hlag.sourceviewer.application.scan.lsp.DiagnosticsCapable;
import com.hlag.sourceviewer.infrastructure.lsp.LoggingLanguageClient;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.ProgressParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.WorkDoneProgressBegin;
import org.eclipse.lsp4j.WorkDoneProgressCreateParams;
import org.eclipse.lsp4j.WorkDoneProgressEnd;
import org.eclipse.lsp4j.WorkDoneProgressNotification;
import org.eclipse.lsp4j.WorkDoneProgressReport;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JDTLS-specific language client that tracks {@code language/status} notifications and
 * {@code window/workDoneProgress} build jobs to provide a reliable readiness signal.
 *
 * <p>The {@link #serviceReadyFuture()} completes (or completes exceptionally) when JDTLS
 * sends its {@code ServiceReady} status event. After that point the readiness strategy must
 * additionally wait for all {@code window/workDoneProgress} build jobs to finish before
 * homo-inference (hover) queries will return meaningful results.</p>
 *
 * <p>Use {@link #activeWorkDoneProgressSnapshot()} to inspect currently running jobs
 * and {@link #activeWorkDoneProgressCount()} for a fast empty-check.</p>
 */
public class JdtlsNotifyingLanguageClient extends LoggingLanguageClient implements JdtlsLanguageClient, DiagnosticsCapable {

    private static final Logger logger = LoggerFactory.getLogger(JdtlsNotifyingLanguageClient.class);

    private static final String MESSAGE_SERVICE_READY = "ServiceReady";
    private static final String TYPE_ERROR = "Error";

    private final CompletableFuture<Void> serviceReadyFuture = new CompletableFuture<>();

    private final Map<String, CountDownLatch> pendingDiagnostics = new ConcurrentHashMap<>();
    private final Set<String> summarizedDiagnosticUris = ConcurrentHashMap.newKeySet();

    /**
     * Token → title map for currently active {@code window/workDoneProgress} jobs.
     * Entries are added on {@code WorkDoneProgressBegin} and removed on {@code WorkDoneProgressEnd}.
     */
    private final Map<String, String> activeWorkDoneProgress = new ConcurrentHashMap<>();

    /**
     * Returns the future that completes when JDTLS reports {@code ServiceReady}, or
     * completes exceptionally if JDTLS reports an error before becoming ready.
     */
    public CompletableFuture<Void> serviceReadyFuture() {
        return serviceReadyFuture;
    }

    /**
     * Returns the number of currently active {@code window/workDoneProgress} jobs.
     *
     * <p>A value of zero means all reported build/indexing jobs have finished.</p>
     */
    public int activeWorkDoneProgressCount() {
        return activeWorkDoneProgress.size();
    }

    /**
     * Returns a snapshot of the currently active {@code window/workDoneProgress} jobs as an
     * unmodifiable map of token → title.
     */
    public Map<String, String> activeWorkDoneProgressSnapshot() {
        return Collections.unmodifiableMap(activeWorkDoneProgress);
    }

    /** @inheritDoc */
    @Override
    public void languageStatus(JdtlsStatusReport report) {
        if (report == null) {
            return;
        }
        logger.debug("JDTLS language/status: type='{}' message='{}'", report.getType(), report.getMessage());

        if (serviceReadyFuture.isDone()) {
            return;
        }

        if (TYPE_ERROR.equalsIgnoreCase(report.getType())) {
            serviceReadyFuture.completeExceptionally(
                    new IllegalStateException(
                            "JDTLS reported an error before becoming ready: " + report.getMessage()));
            return;
        }

        if (MESSAGE_SERVICE_READY.equalsIgnoreCase(report.getMessage())) {
            logger.info("JDTLS reports ServiceReady — language server is ready");
            serviceReadyFuture.complete(null);
        }
    }

    /** @inheritDoc */
    @Override
    public CompletableFuture<Void> createProgress(WorkDoneProgressCreateParams params) {
        String token = tokenToString(params.getToken());
        logger.debug("JDTLS window/workDoneProgress/create: token='{}'", token);
        return CompletableFuture.completedFuture(null);
    }

    /** @inheritDoc */
    @Override
    public void notifyProgress(ProgressParams params) {
        super.notifyProgress(params);

        String token = tokenToString(params.getToken());
        Either<WorkDoneProgressNotification, Object> value = params.getValue();
        if (value == null) {
            return;
        }

        WorkDoneProgressNotification notification = value.isLeft() ? value.getLeft() : null;
        if (notification instanceof WorkDoneProgressBegin begin) {
            activeWorkDoneProgress.put(token, begin.getTitle() != null ? begin.getTitle() : token);
            logger.debug(
                    "JDTLS progress begin: token='{}' title='{}' message='{}' percentage={}",
                    token,
                    begin.getTitle(),
                    begin.getMessage(),
                    begin.getPercentage());
        } else if (notification instanceof WorkDoneProgressReport report) {
            logger.debug(
                    "JDTLS progress report: token='{}' message='{}' percentage={}",
                    token,
                    report.getMessage(),
                    report.getPercentage());
        } else if (notification instanceof WorkDoneProgressEnd end) {
            String title = activeWorkDoneProgress.remove(token);
            logger.debug(
                    "JDTLS progress end: token='{}' title='{}' message='{}' remaining={}",
                    token,
                    title,
                    end.getMessage(),
                    activeWorkDoneProgress.size());
        } else {
            logger.debug("JDTLS $/progress: token='{}' value='{}'", token, value);
        }
    }

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
        super.publishDiagnostics(diagnostics);

        String uri = diagnostics.getUri();
        List<Diagnostic> diags = diagnostics.getDiagnostics();
        logger.debug("JDTLS diagnostics for {}: {} entries", uri, diags.size());

        // Warten-Latch auflösen, falls jemand auf diese URI wartet
        CountDownLatch latch = pendingDiagnostics.remove(uri);
        if (latch != null) {
            latch.countDown();
        }
    }

    public void waitForDiagnostics(String uri) {
        pendingDiagnostics.put(uri, new CountDownLatch(1));
    }

    public boolean awaitDiagnostics(String uri) {
        return awaitDiagnostics(uri, 30, TimeUnit.SECONDS);
    }

    public boolean awaitDiagnostics(String uri, long timeout, TimeUnit unit) {
        logger.trace("[JDTLS] Awaiting JDTLS diagnostics for {}", uri);
        CountDownLatch latch = pendingDiagnostics.get(uri);
        if (latch == null) {
            logger.warn("[JDTLS] awaitDiagnostics() called without previous waitForDiagnostics() for: {}", uri);
            return false;
        }

        try {
            boolean received = latch.await(timeout, unit);
            if (!received) {
                logger.warn("[JDTLS] Timeout:  no diagnostics received for {}", uri);
                pendingDiagnostics.remove(uri);
            }
            logger.trace("[JDTLS] Successfully await diagnostics for {}", uri);
            return received;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** @inheritDoc */
    @Override
    public void languageEventNotification(Object eventNotification) {
        logger.debug("JDTLS language/eventNotification: {}", eventNotification);
    }

    private static String tokenToString(Either<String, Integer> token) {
        if (token == null) {
            return "unknown";
        }
        return token.isLeft() ? token.getLeft() : String.valueOf(token.getRight());
    }
}