package com.disasterrelief.backend.ai;

/** The only place that talks to the language model, so tests can replace it. */
interface ModelClient {

    boolean configured();

    String model();

    /** @throws ModelUnavailableException on timeout, network error, refusal or an HTTP error */
    String complete(String system, String userContent);
}
