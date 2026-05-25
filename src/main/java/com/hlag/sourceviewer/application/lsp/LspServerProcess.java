package com.hlag.sourceviewer.application.lsp;

import com.hlag.sourceviewer.domain.model.source.TokenHoverEntry;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolCapabilities;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.HoverCapabilities;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Wraps a running LSP server process and provides synchronised access to
 * common LSP operations used during scanning and interactive hover queries.
 *
 * <p>All public methods are {@code synchronized} to ensure that the document
 * open/query/close sequence is never interleaved across concurrent callers.</p>
 */
public class LspServerProcess implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(LspServerProcess.class);

    private static final int REQUEST_TIMEOUT_SECONDS = 15;

    private final Process process;
    private final LanguageServer server;
    private final Future<?> listeningFuture;
    private final String language;
    private final Path workspacePath;
    private volatile Instant lastAccessed;

    private LspServerProcess(Process process, LanguageServer server, Future<?> listeningFuture,
                              String language, Path workspacePath) {
        this.process = process;
        this.server = server;
        this.listeningFuture = listeningFuture;
        this.language = language;
        this.workspacePath = workspacePath;
        this.lastAccessed = Instant.now();
    }

    /**
     * Starts an LSP server using the given command, initialises the LSP handshake
     * for the given workspace, and returns a ready-to-use {@code LspServerProcess}.
     */
    static LspServerProcess start(List<String> command, String language, Path workspacePath) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process process = pb.start();

        NoopLanguageClient client = new NoopLanguageClient();
        Launcher<LanguageServer> launcher = LSPLauncher.createClientLauncher(
                client, process.getInputStream(), process.getOutputStream());

        Future<?> listeningFuture = launcher.startListening();
        LanguageServer server = launcher.getRemoteProxy();

        InitializeParams initParams = new InitializeParams();
        initParams.setRootUri(workspacePath.toUri().toString());
        initParams.setCapabilities(buildClientCapabilities());

        try {
            server.initialize(initParams).get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            server.initialized(new InitializedParams());
        } catch (Exception e) {
            process.destroyForcibly();
            throw new IOException("LSP initialisation failed for " + language + " at " + workspacePath, e);
        }

        logger.info("LSP server started for language={} workspace={}", language, workspacePath);
        return new LspServerProcess(process, server, listeningFuture, language, workspacePath);
    }

    /**
     * Fetches all symbol declarations in the given file by sending
     * {@code textDocument/documentSymbol} to the server.
     *
     * <p>Opens the document, queries symbols, then closes it again.</p>
     */
    public synchronized List<DocumentSymbol> documentSymbols(String uri, String content) {
        lastAccessed = Instant.now();
        try {
            openDocument(uri, content);
            DocumentSymbolParams params = new DocumentSymbolParams(new TextDocumentIdentifier(uri));
            var result = server.getTextDocumentService()
                    .documentSymbol(params)
                    .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (result == null) {
                return List.of();
            }
            return result.stream()
                    .filter(e -> e.isRight())
                    .map(e -> e.getRight())
                    .toList();
        } catch (TimeoutException e) {
            logger.warn("documentSymbol timed out for {}", uri);
            return List.of();
        } catch (Exception e) {
            logger.warn("documentSymbol failed for {}: {}", uri, e.getMessage());
            return List.of();
        } finally {
            closeDocument(uri);
        }
    }

    /**
     * Queries hover and definition info for a batch of token positions in a single file.
     * The document is opened once, all positions queried sequentially, then closed.
     *
     * <p>Each entry in the returned list corresponds to the position at the same index in
     * {@code zeroBasedPositions}. Entries where the LSP returned no data are omitted —
     * the list may be shorter than the input.</p>
     *
     * @param zeroBasedPositions each element is {@code int[]{line, col}} (0-based, LSP convention)
     * @return non-null list of hover entries that have at least markdown or a definition
     */
    public synchronized List<TokenHoverEntry> collectTokenHovers(
            String uri, String content, List<int[]> zeroBasedPositions) {
        lastAccessed = Instant.now();
        List<TokenHoverEntry> results = new ArrayList<>();
        try {
            openDocument(uri, content);
            for (int[] pos : zeroBasedPositions) {
                int line = pos[0];
                int col  = pos[1];
                String markdown = queryHoverAt(uri, line, col).orElse(null);
                Location def    = queryDefinitionAt(uri, line, col).orElse(null);

                String defPath = null;
                Integer defLine = null;
                Integer defCol  = null;
                if (def != null) {
                    defPath = def.getUri();
                    defLine = def.getRange().getStart().getLine() + 1;
                    defCol  = def.getRange().getStart().getCharacter() + 1;
                }
                if (markdown != null || defPath != null) {
                    // +1 converts back to 1-based domain convention
                    results.add(new TokenHoverEntry(line + 1, col + 1, markdown, defPath, defLine, defCol));
                }
            }
        } catch (Exception e) {
            logger.warn("collectTokenHovers failed for {}: {}", uri, e.getMessage());
        } finally {
            closeDocument(uri);
        }
        return results;
    }

    private Optional<String> queryHoverAt(String uri, int zeroBasedLine, int zeroBasedCol) {
        try {
            HoverParams params = new HoverParams(
                    new TextDocumentIdentifier(uri),
                    new Position(zeroBasedLine, zeroBasedCol));
            var hover = server.getTextDocumentService()
                    .hover(params)
                    .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (hover == null || hover.getContents() == null) {
                return Optional.empty();
            }
            var contents = hover.getContents();
            if (contents.isRight()) {
                MarkupContent mc = contents.getRight();
                return Optional.ofNullable(mc.getValue()).filter(s -> !s.isBlank());
            }
            return Optional.empty();
        } catch (TimeoutException e) {
            logger.warn("hover timed out for {} at {}:{}", uri, zeroBasedLine, zeroBasedCol);
            return Optional.empty();
        } catch (Exception e) {
            logger.warn("hover failed for {} at {}:{}: {}", uri, zeroBasedLine, zeroBasedCol, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<Location> queryDefinitionAt(String uri, int zeroBasedLine, int zeroBasedCol) {
        try {
            DefinitionParams params = new DefinitionParams(
                    new TextDocumentIdentifier(uri),
                    new Position(zeroBasedLine, zeroBasedCol));
            var result = server.getTextDocumentService()
                    .definition(params)
                    .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (result == null) {
                return Optional.empty();
            }
            if (result.isLeft()) {
                List<? extends Location> locs = result.getLeft();
                return locs.isEmpty() ? Optional.empty() : Optional.of(locs.get(0));
            }
            if (result.isRight()) {
                List<? extends LocationLink> links = result.getRight();
                if (!links.isEmpty()) {
                    LocationLink link = links.get(0);
                    Location loc = new Location();
                    loc.setUri(link.getTargetUri());
                    loc.setRange(link.getTargetRange());
                    return Optional.of(loc);
                }
            }
            return Optional.empty();
        } catch (TimeoutException e) {
            logger.warn("definition timed out for {} at {}:{}", uri, zeroBasedLine, zeroBasedCol);
            return Optional.empty();
        } catch (Exception e) {
            logger.warn("definition failed for {} at {}:{}: {}", uri, zeroBasedLine, zeroBasedCol, e.getMessage());
            return Optional.empty();
        }
    }

    public boolean isAlive() {
        return process.isAlive() && !listeningFuture.isDone();
    }

    public Instant lastAccessed() {
        return lastAccessed;
    }

    public String language() {
        return language;
    }

    public Path workspacePath() {
        return workspacePath;
    }

    @Override
    public void close() {
        logger.info("Stopping LSP server for language={} workspace={}", language, workspacePath);
        try {
            CompletableFuture<Object> shutdown = server.shutdown();
            shutdown.get(5, TimeUnit.SECONDS);
            server.exit();
        } catch (Exception e) {
            logger.debug("LSP shutdown did not complete cleanly: {}", e.getMessage());
        } finally {
            process.destroyForcibly();
            listeningFuture.cancel(true);
        }
    }

    private void openDocument(String uri, String content) {
        DidOpenTextDocumentParams params = new DidOpenTextDocumentParams();
        params.setTextDocument(new TextDocumentItem(uri, language, 1, content));
        server.getTextDocumentService().didOpen(params);
    }

    private void closeDocument(String uri) {
        try {
            DidCloseTextDocumentParams params = new DidCloseTextDocumentParams();
            params.setTextDocument(new TextDocumentIdentifier(uri));
            server.getTextDocumentService().didClose(params);
        } catch (Exception e) {
            logger.debug("didClose failed for {}: {}", uri, e.getMessage());
        }
    }

    private static ClientCapabilities buildClientCapabilities() {
        var hover = new HoverCapabilities();
        hover.setContentFormat(List.of("markdown", "plaintext"));

        var docSymbol = new DocumentSymbolCapabilities();
        docSymbol.setHierarchicalDocumentSymbolSupport(true);

        var textDoc = new TextDocumentClientCapabilities();
        textDoc.setHover(hover);
        textDoc.setDocumentSymbol(docSymbol);

        var workspace = new WorkspaceClientCapabilities();

        var caps = new ClientCapabilities();
        caps.setTextDocument(textDoc);
        caps.setWorkspace(workspace);
        return caps;
    }

    /** Minimal no-op LanguageClient required by LSP4J for client-side notifications. */
    private static class NoopLanguageClient implements LanguageClient {
        @Override public void telemetryEvent(Object object) {}
        @Override public void publishDiagnostics(org.eclipse.lsp4j.PublishDiagnosticsParams diagnostics) {}
        @Override public void showMessage(org.eclipse.lsp4j.MessageParams messageParams) {}
        @Override public CompletableFuture<org.eclipse.lsp4j.MessageActionItem> showMessageRequest(
                org.eclipse.lsp4j.ShowMessageRequestParams requestParams) {
            return CompletableFuture.completedFuture(null);
        }
        @Override public void logMessage(org.eclipse.lsp4j.MessageParams message) {}
    }
}
