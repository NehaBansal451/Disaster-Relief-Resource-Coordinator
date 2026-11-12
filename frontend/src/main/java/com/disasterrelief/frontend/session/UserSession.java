package com.disasterrelief.frontend.session;

import com.disasterrelief.frontend.api.AuthUser;
import com.disasterrelief.frontend.api.LoginResult;
import com.vaadin.flow.server.VaadinSession;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Holds the backend access token and user in the server-side Vaadin session (architecture decision A5).
 * The token is never written to the page, to browser storage, or to a cookie.
 */
@Component
public class UserSession {

    private static final String KEY = "drrc.login";

    /** The token, who it belongs to, and when it stops being valid. */
    public record Login(String token, AuthUser user, Instant expiresAt) {
        boolean isValidAt(Instant now) {
            return now.isBefore(expiresAt);
        }
    }

    public void login(LoginResult result) {
        Instant expiresAt = Instant.now().plus(Duration.ofSeconds(result.expiresIn()));
        VaadinSession.getCurrent().setAttribute(KEY, new Login(result.accessToken(), result.user(), expiresAt));
    }

    /** The current login, or empty when nobody is logged in or the token has expired. */
    public Optional<Login> current() {
        VaadinSession vaadinSession = VaadinSession.getCurrent();
        if (vaadinSession == null) {
            return Optional.empty();
        }
        Object value = vaadinSession.getAttribute(KEY);
        if (value instanceof Login login && login.isValidAt(Instant.now())) {
            return Optional.of(login);
        }
        return Optional.empty();
    }

    /** Token for backend calls. Callers must have passed the route guard, so a missing login is a programming error. */
    public String token() {
        return current().map(Login::token).orElseThrow(() -> new IllegalStateException("Not logged in."));
    }

    public AuthUser user() {
        return current().map(Login::user).orElseThrow(() -> new IllegalStateException("Not logged in."));
    }

    /** Ends the session on the server. The caller then navigates to the login page. */
    public void logout() {
        VaadinSession vaadinSession = VaadinSession.getCurrent();
        if (vaadinSession != null) {
            vaadinSession.setAttribute(KEY, null);
            if (vaadinSession.getSession() != null) {
                vaadinSession.getSession().invalidate();
            }
        }
    }
}
