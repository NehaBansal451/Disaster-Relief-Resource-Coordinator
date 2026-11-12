package com.disasterrelief.backend.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /api/auth/login body. The password is bounded so a huge value cannot be used to waste CPU in BCrypt. */
public record LoginRequest(
        @NotBlank @Size(max = 255) String email,
        @NotBlank @Size(max = 200) String password) {
}
