package com.disasterrelief.backend.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AiRateLimiterTest {

    @Test
    void refusesBeyondTheLimitAndAllowsAgainWhenTheWindowPasses() {
        AiRateLimiter limiter = new AiRateLimiter(2, 1000);
        assertTrue(limiter.tryAcquire(1, 0));
        assertTrue(limiter.tryAcquire(1, 1));
        assertFalse(limiter.tryAcquire(1, 2));
        assertTrue(limiter.tryAcquire(1, 1000));
    }

    @Test
    void usersAreIndependent() {
        AiRateLimiter limiter = new AiRateLimiter(1, 1000);
        assertTrue(limiter.tryAcquire(1, 0));
        assertFalse(limiter.tryAcquire(1, 1));
        assertTrue(limiter.tryAcquire(2, 1));
    }
}
