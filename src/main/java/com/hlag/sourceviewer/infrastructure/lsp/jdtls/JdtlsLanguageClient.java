package com.hlag.sourceviewer.infrastructure.lsp.jdtls;

import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.services.LanguageClient;

/**
 * Extended {@link LanguageClient} interface that receives JDTLS-specific notifications.
 *
 * <p>LSP4J's {@code GenericEndpoint} traverses all interfaces of the local service object and
 * registers every {@code @JsonNotification}-annotated method as a handler. Implementing this
 * interface therefore causes the JSON-RPC launcher to route incoming {@code language/status}
 * messages to {@link #languageStatus(JdtlsStatusReport)}.</p>
 */
public interface JdtlsLanguageClient extends LanguageClient {

    /**
     * Receives the JDTLS {@code language/status} notification.
     *
     * <p>The server sends this notification during startup, indexing, and on completion.
     * When {@link JdtlsStatusReport#getMessage()} equals {@code "ServiceReady"} the language
     * server is fully indexed and ready to process requests.</p>
     *
     * @param report the status report from JDTLS
     */
    @JsonNotification("language/status")
    void languageStatus(JdtlsStatusReport report);
}

