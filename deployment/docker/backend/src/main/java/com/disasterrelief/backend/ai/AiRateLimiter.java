package com.disasterrelief.backend.ai;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Sliding-window limit per user (default 10 analyses per hour) so one account cannot run up the model bill. */
@Component
class AiRateLimiter {

    private final int max;
    private final long windowMillis;
    private final Map<Long, Deque<Long>> calls = new HashMap<>();

    @Autowired
    AiRateLimiter(@Value("${drrc.ai.max-per-hour:10}") int max) {
        this(max, 3_600_000L);
    }

    AiRateLimiter(int max, long windowMillis) {
        this.max = max;
        this.windowMillis = windowMillis;
    }

    synchronized boolean tryAcquire(long userId, long nowMillis) {
        Deque<Long> times = calls.computeIfAbsent(userId, k -> new ArrayDeque<>());
        while (!times.isEmpty() && nowMillis - times.peekFirst() >= windowMillis) {
            times.pollFirst();
        }
        if (times.size() >= max) {
            return false;
        }
        times.addLast(nowMillis);
        return true;
    }
}
