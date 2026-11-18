package com.disasterrelief.backend.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LoginThrottleTest {

    private static final long MINUTE = 60_000L;

    @Test
    void blocksAfterTooManyFailuresForOneEmailAndLiftsAfterTheWindow() {
        LoginThrottle t = new LoginThrottle(3, 100, 10 * MINUTE);
        for (int i = 0; i < 3; i++) {
            assertFalse(t.blocked("A@x.org", "1.1.1." + i, i));
            t.recordFailure("A@x.org", "1.1.1." + i, i);
        }
        assertTrue(t.blocked("a@x.org", "9.9.9.9", 10), "e-mail match is case-insensitive");
        assertFalse(t.blocked("other@x.org", "9.9.9.9", 10));
        assertFalse(t.blocked("a@x.org", "9.9.9.9", 10 * MINUTE + 5), "window passed");
    }

    @Test
    void blocksOneAddressThatTriesManyEmails() {
        LoginThrottle t = new LoginThrottle(100, 4, 10 * MINUTE);
        for (int i = 0; i < 4; i++) {
            t.recordFailure("user" + i + "@x.org", "5.5.5.5", i);
        }
        assertTrue(t.blocked("fresh@x.org", "5.5.5.5", 10));
        assertFalse(t.blocked("fresh@x.org", "6.6.6.6", 10));
    }

    @Test
    void successClearsTheEmailCounter() {
        LoginThrottle t = new LoginThrottle(2, 100, 10 * MINUTE);
        t.recordFailure("a@x.org", "1.1.1.1", 1);
        t.recordSuccess("a@x.org");
        t.recordFailure("a@x.org", "1.1.1.1", 2);
        assertFalse(t.blocked("a@x.org", "1.1.1.1", 3));
    }
}
