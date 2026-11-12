package com.disasterrelief.backend.auth;

/** Frozen contract: {accessToken, tokenType:"Bearer", expiresIn:3600, user:{id, fullName, email, role}}. */
public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserSummary user) {
}
