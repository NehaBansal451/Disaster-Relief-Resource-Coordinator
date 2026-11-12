package com.disasterrelief.backend.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.disasterrelief.backend.common.ApiException;
import com.disasterrelief.backend.security.JwtService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository users;
    @Mock
    private JwtService jwtService;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4); // low cost: fast tests only
    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, encoder, jwtService);
    }

    private AppUser user(boolean enabled) {
        return new AppUser(5L, "Demo Officer", "officer@demo.drrc.local", encoder.encode("Demo@12345"),
                Role.OFFICER, enabled, "en");
    }

    @Test
    void successfulLoginReturnsTokenAndUserWithoutHash() {
        when(users.findByEmailIgnoreCase("officer@demo.drrc.local")).thenReturn(Optional.of(user(true)));
        when(jwtService.issue(any())).thenReturn(new JwtService.IssuedToken("tok", 3600));

        LoginResponse response = service.login(new LoginRequest("  officer@demo.drrc.local ", "Demo@12345"));

        assertEquals("tok", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(3600, response.expiresIn());
        assertEquals(Role.OFFICER, response.user().role());
        assertEquals("officer@demo.drrc.local", response.user().email());
    }

    @Test
    void wrongPasswordIsUnauthenticatedWithGenericMessage() {
        when(users.findByEmailIgnoreCase("officer@demo.drrc.local")).thenReturn(Optional.of(user(true)));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.login(new LoginRequest("officer@demo.drrc.local", "wrong")));

        assertEquals(401, ex.status().value());
        assertEquals("UNAUTHENTICATED", ex.code());
        assertEquals(AuthService.INVALID_CREDENTIALS, ex.getMessage());
        verify(jwtService, never()).issue(any());
    }

    @Test
    void unknownEmailGivesTheSameErrorAsWrongPassword() {
        when(users.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> service.login(new LoginRequest("nobody@example.com", "whatever")));

        assertEquals(AuthService.INVALID_CREDENTIALS, ex.getMessage());
        verify(jwtService, never()).issue(any());
    }

    @Test
    void disabledAccountCannotLogInEvenWithCorrectPassword() {
        when(users.findByEmailIgnoreCase("officer@demo.drrc.local")).thenReturn(Optional.of(user(false)));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.login(new LoginRequest("officer@demo.drrc.local", "Demo@12345")));

        assertEquals(AuthService.INVALID_CREDENTIALS, ex.getMessage());
        verify(jwtService, never()).issue(any());
    }

    @Test
    void currentUserResolvesEnabledAccountOnly() {
        when(users.findById(5L)).thenReturn(Optional.of(user(true)));
        assertEquals("Demo Officer", service.currentUser(5L).fullName());

        when(users.findById(6L)).thenReturn(Optional.of(user(false)));
        assertThrows(ApiException.class, () -> service.currentUser(6L));

        when(users.findById(7L)).thenReturn(Optional.empty());
        assertThrows(ApiException.class, () -> service.currentUser(7L));
    }

    @Test
    void passwordHashIsNeverPartOfTheUserSummary() {
        String text = UserSummary.from(user(true)).toString();
        assertFalse(text.contains("$2"));
    }
}
