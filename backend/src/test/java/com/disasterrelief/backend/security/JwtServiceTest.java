package com.disasterrelief.backend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.disasterrelief.backend.auth.AppUser;
import com.disasterrelief.backend.auth.Role;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtServiceTest {

    // Test-only key. Not a real secret.
    private static final String SECRET = "unit-test-secret-key-0123456789-abcdefghij";

    private static SecretKey key(String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private static JwtService service(long ttl) {
        return new JwtService(new NimbusJwtEncoder(new ImmutableSecret<>(key(SECRET))), ttl);
    }

    private static JwtDecoder decoder(String secret) {
        return NimbusJwtDecoder.withSecretKey(key(secret)).macAlgorithm(MacAlgorithm.HS256).build();
    }

    private static AppUser officer() {
        return new AppUser(7L, "Demo Officer", "officer@demo.drrc.local", "hash", Role.OFFICER, true, "en");
    }

    @Test
    void issuedTokenCarriesSubjectRoleAndExpiry() {
        JwtService.IssuedToken token = service(3600).issue(officer());

        Jwt jwt = decoder(SECRET).decode(token.value());
        assertEquals("7", jwt.getSubject());
        assertEquals("OFFICER", jwt.getClaimAsString(JwtService.ROLE_CLAIM));
        assertEquals(3600, token.expiresInSeconds());
        assertEquals(Duration.ofSeconds(3600), Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()));
    }

    @Test
    void tokenContainsNoEmailOrName() {
        Jwt jwt = decoder(SECRET).decode(service(3600).issue(officer()).value());
        assertTrue(jwt.getClaims().keySet().stream().noneMatch(k -> k.equals("email") || k.equals("name")));
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String token = service(3600).issue(officer()).value();
        assertThrows(JwtException.class, () -> decoder("another-secret-key-0123456789-abcdefghijk").decode(token));
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = service(3600).issue(officer()).value();
        String tampered = token.substring(0, token.length() - 3) + (token.endsWith("AAA") ? "BBB" : "AAA");
        assertThrows(JwtException.class, () -> decoder(SECRET).decode(tampered));
    }

    @Test
    void rejectsTooShortLifetime() {
        assertThrows(IllegalStateException.class, () -> service(10));
    }
}
