package com.disasterrelief.backend.common;

import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * A business-rule failure with an HTTP status and a stable machine-readable code
 * (see docs/API_CONTRACT.md "Common error codes"). Turned into application/problem+json by {@link GlobalExceptionHandler}.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final List<FieldProblem> errors;

    public ApiException(HttpStatus status, String code, String message, List<FieldProblem> errors) {
        super(message);
        this.status = status;
        this.code = code;
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public List<FieldProblem> errors() {
        return errors;
    }

    public static ApiException validation(List<FieldProblem> errors) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed.", errors);
    }

    public static ApiException validation(String field, String message) {
        return validation(List.of(new FieldProblem(field, message)));
    }

    public static ApiException unauthenticated(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", message, null);
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message, null);
    }

    public static ApiException invalidState(String message) {
        return new ApiException(HttpStatus.CONFLICT, "INVALID_STATE", message, null);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message, null);
    }
}
