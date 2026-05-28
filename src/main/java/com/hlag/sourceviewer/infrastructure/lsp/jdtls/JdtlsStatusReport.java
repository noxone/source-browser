package com.hlag.sourceviewer.infrastructure.lsp.jdtls;

/**
 * Notification payload for the JDTLS-specific {@code language/status} notification.
 *
 * <p>JDTLS sends this notification throughout its startup and indexing lifecycle.
 * When {@code type} is {@code "Started"} and {@code message} is {@code "ServiceReady"},
 * the server has finished indexing and is ready to serve requests.</p>
 */
public class JdtlsStatusReport {

    private String type;
    private String message;

    /** Required by the LSP4J JSON deserialiser. */
    public JdtlsStatusReport() {
    }

    /** Creates a report with the given type and message. */
    public JdtlsStatusReport(String type, String message) {
        this.type = type;
        this.message = message;
    }

    /** Returns the status type (for example {@code "Starting"}, {@code "Started"}, or {@code "Error"}). */
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /** Returns the status message (for example {@code "ServiceReady"} when fully initialised). */
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "JdtlsStatusReport{type='" + type + "', message='" + message + "'}";
    }
}

