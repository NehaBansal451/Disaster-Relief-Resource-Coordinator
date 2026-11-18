package com.disasterrelief.backend.ai;

class ModelUnavailableException extends RuntimeException {

    ModelUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
