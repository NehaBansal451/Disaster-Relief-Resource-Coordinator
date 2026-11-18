package com.disasterrelief.backend.auth;

import com.disasterrelief.backend.common.ApiException;
import com.disasterrelief.backend.security.CurrentUser;
import com.disasterrelief.backend.security.LoginThrottle;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** POST /api/auth/login (public) and GET /api/auth/me (any authenticated user). See docs/API_CONTRACT.md. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final LoginThrottle throttle;

    public AuthController(AuthService authService, LoginThrottle throttle) {
        this.authService = authService;
        this.throttle = throttle;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        String address = http.getRemoteAddr();
        long now = System.currentTimeMillis();
        if (throttle.blocked(request.email(), address, now)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Too many failed sign-in attempts. Try again in a few minutes.", null);
        }
        try {
            LoginResponse response = authService.login(request);
            throttle.recordSuccess(request.email());
            return response;
        } catch (ApiException ex) {
            if (ex.status() == HttpStatus.UNAUTHORIZED) {
                throttle.recordFailure(request.email(), address, now);
            }
            throw ex;
        }
    }

    @GetMapping("/me")
    public UserSummary me(@AuthenticationPrincipal Jwt jwt) {
        return authService.currentUser(CurrentUser.id(jwt));
    }
}
