package com.simfat.backend.exception;

import java.time.LocalDateTime;
import java.util.Map;

public class ApiError {

    private boolean success;
    private int status;
    private String error;
    private String message;
    private String path;
    private LocalDateTime timestamp;
    private Map<String, String> validationErrors;

    public static ApiError of(int status, String error, String message, String path) {
        ApiError apiError = new ApiError();
        apiError.setSuccess(false);
        apiError.setStatus(status);
        apiError.setError(error);
        apiError.setMessage(message);
        apiError.setPath(path);
        apiError.setTimestamp(LocalDateTime.now());
        return apiError;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, String> getValidationErrors() {
        return validationErrors;
    }

    public void setValidationErrors(Map<String, String> validationErrors) {
        this.validationErrors = validationErrors;
    }
}

