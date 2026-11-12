package com.disasterrelief.backend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class SecurityConfigTest {

    @Test
    void refusesASigningSecretShorterThan32Bytes() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new SecurityConfig().jwtSigningKey("too-short"));
        assertEquals(true, ex.getMessage().contains("JWT_SECRET"));
    }

    @Test
    void acceptsA32ByteSecret() {
        SecretKey key = new SecurityConfig().jwtSigningKey("0123456789abcdef0123456789abcdef");
        assertEquals("HmacSHA256", key.getAlgorithm());
    }
}
