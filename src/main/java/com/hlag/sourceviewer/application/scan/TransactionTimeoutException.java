package com.hlag.sourceviewer.application.scan;

class TransactionTimeoutException extends RuntimeException {
    TransactionTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}