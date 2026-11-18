package com.disasterrelief.backend.ai;

/** The model's answer was not valid JSON or broke the agreed schema. */
class AnalysisFormatException extends RuntimeException {

    AnalysisFormatException(String message) {
        super(message);
    }
}
