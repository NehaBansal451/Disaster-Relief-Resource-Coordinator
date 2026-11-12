package com.disasterrelief.frontend.api;

/** Backend login response. The token stays on the Vaadin server session and is never sent to the browser. */
public record LoginResult(String accessToken, String tokenType, long expiresIn, AuthUser user) {
}
