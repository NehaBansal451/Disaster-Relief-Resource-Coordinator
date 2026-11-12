package com.disasterrelief.backend.common;

/** One field-level validation problem, serialised inside the "errors" array of an error response. */
public record FieldProblem(String field, String message) {
}
