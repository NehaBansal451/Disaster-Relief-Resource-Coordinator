package com.disasterrelief.backend.security;

import com.disasterrelief.backend.auth.AppUser;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues signed access tokens (HS256). The token carries only the user id (subject) and the role, nothing personal.
 * No refresh tokens in the MVP (architecture decision A6).
 */
@Service
public class JwtService {

    public static final String ROLE_CLAIM = "role";
    static final String ISSUER = "drrc-backend";

    /** A signed token and how many seconds it stays valid. */
    public record IssuedToken(String value, long expiresInSeconds) {
    }

    private final JwtEncoder encoder;
    private final long ttlSeconds;

    public JwtService(JwtEncoder encoder, @Value("${drrc.security.jwt-ttl-seconds:3600}") long ttlSeconds) {
        if (ttlSeconds < 60) {
            throw new IllegalStateException("drrc.security.jwt-ttl-seconds must be at least 60.");
        }
        this.encoder = encoder;
        this.ttlSeconds = ttlSeconds;
    }

    public IssuedToken issue(AppUser user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ttlSeconds))
                .subject(String.valueOf(user.getId()))
                .claim(ROLE_CLAIM, user.getRole().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, ttlSeconds);
    }
}
