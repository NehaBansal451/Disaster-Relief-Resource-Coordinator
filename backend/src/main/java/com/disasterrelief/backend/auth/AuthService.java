package com.disasterrelief.backend.auth;

import com.disasterrelief.backend.common.ApiException;
import com.disasterrelief.backend.security.JwtService;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    static final String INVALID_CREDENTIALS = "Invalid email or password.";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    /** Hash of a random value, compared against when the email is unknown so response time does not reveal it. */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Optional<AppUser> found = users.findByEmailIgnoreCase(request.email().trim());
        if (found.isEmpty()) {
            passwordEncoder.matches(request.password(), dummyHash);
            throw ApiException.unauthenticated(INVALID_CREDENTIALS);
        }
        AppUser user = found.get();
        boolean passwordOk = passwordEncoder.matches(request.password(), user.getPasswordHash());
        // Same message for unknown email, wrong password and disabled account: no account enumeration.
        if (!passwordOk || !user.isEnabled()) {
            throw ApiException.unauthenticated(INVALID_CREDENTIALS);
        }
        JwtService.IssuedToken token = jwtService.issue(user);
        return new LoginResponse(token.value(), "Bearer", token.expiresInSeconds(), UserSummary.from(user));
    }

    /** Current user from the database (not from the token), so a disabled account stops resolving here. */
    @Transactional(readOnly = true)
    public UserSummary currentUser(long userId) {
        AppUser user = users.findById(userId)
                .filter(AppUser::isEnabled)
                .orElseThrow(() -> ApiException.unauthenticated("Account not found or disabled."));
        return UserSummary.from(user);
    }
}
