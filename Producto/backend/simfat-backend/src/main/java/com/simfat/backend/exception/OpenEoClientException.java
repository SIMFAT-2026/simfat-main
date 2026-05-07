package com.simfat.backend.exception;

public class OpenEoClientException extends RuntimeException {

    private final Integer statusCode;
    private final boolean transientError;

    public OpenEoClientException(String message, Integer statusCode, boolean transientError, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.transientError = transientError;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public boolean isTransientError() {
        return transientError;
    }
}
