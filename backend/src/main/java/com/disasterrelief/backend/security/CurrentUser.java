package com.disasterrelief.backend.security;

import com.disasterrelief.backend.common.ApiException;
import org.springframework.security.oauth2.jwt.Jwt;

/** Reads the authenticated user's id from the token subject. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static long id(Jwt jwt) {
        try {
            return Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException | NullPointerException ex) {
            throw ApiException.unauthenticated("Invalid access token.");
        }
    }
}
