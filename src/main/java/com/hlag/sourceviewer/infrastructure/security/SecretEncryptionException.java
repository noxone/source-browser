package com.hlag.sourceviewer.infrastructure.security;

/**
 * Thrown when AES-256-GCM encryption or decryption of a Git credential secret fails.
 */
public class SecretEncryptionException extends RuntimeException {

    /**
     * Creates a new exception with the given message and root cause.
     *
     * @param message human-readable description of the failure
     * @param cause   the underlying cryptographic exception
     */
    public SecretEncryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
