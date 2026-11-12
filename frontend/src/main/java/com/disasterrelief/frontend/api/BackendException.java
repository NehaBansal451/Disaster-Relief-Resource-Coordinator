package com.disasterrelief.frontend.api;

import java.util.Map;

/**
 * A failed call to the backend, already translated into something a view can show.
 * status 0 means the backend could not be reached at all (connection refused or timeout).
 */
public class BackendException extends RuntimeException {

    private final int status;
    private final String code;
    private final Map<String, String> fieldErrors;

    public BackendException(int status, String code, String message, Map<String, String> fieldErrors) {
        super(message);
        this.status = status;
        this.code = code;
        this.fieldErrors = fieldErrors == null ? Map.of() : Map.copyOf(fieldErrors);
    }

    public int status() {
        return status;
    }

    /** Stable backend error code such as VALIDATION_ERROR or INVALID_STATE; null when unreachable. */
    public String code() {
        return code;
    }

    /** Field name to message, from the backend "errors" array. Empty when not a validation error. */
    public Map<String, String> fieldErrors() {
        return fieldErrors;
    }

    public boolean isUnreachable() {
        return status == 0;
    }

    public boolean isUnauthenticated() {
        return status == 401;
    }

    public boolean isForbidden() {
        return status == 403;
    }
}
