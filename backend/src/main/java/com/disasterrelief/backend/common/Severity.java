package com.disasterrelief.backend.common;

/** Severity scale shared by disasters (severity) and affected areas (impact level). Matches the DB CHECK constraints. */
public enum Severity {
    CRITICAL, HIGH, MEDIUM, LOW
}
