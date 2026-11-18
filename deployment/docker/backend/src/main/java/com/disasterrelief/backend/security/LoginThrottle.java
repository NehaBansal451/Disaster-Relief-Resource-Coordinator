package com.disasterrelief.backend.security;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Slows down password guessing: after too many FAILED logins for one e-mail (default 5) or from one address
 * (default 30) within the window (default 15 minutes), further attempts are refused with HTTP 429 until the oldest
 * failure leaves the window. A successful login clears the e-mail's count. State is in memory (one server instance);
 * a restart resets it. For several instances, move this to the database or a shared cache.
 */
@Component
public class LoginThrottle {

    private static final int MAX_KEYS = 10_000;

    private final int perEmail;
    private final int perAddress;
    private final long windowMillis;
    private final Map<String, Deque<Long>> failures = new HashMap<>();

    @Autowired
    public LoginThrottle(@Value("${drrc.security.login-max-failures-per-email:5}") int perEmail,
                         @Value("${drrc.security.login-max-failures-per-address:30}") int perAddress) {
        this(perEmail, perAddress, 15 * 60_000L);
    }

    LoginThrottle(int perEmail, int perAddress, long windowMillis) {
        this.perEmail = perEmail;
        this.perAddress = perAddress;
        this.windowMillis = windowMillis;
    }

    /** @return true when this attempt must be refused without even checking the password */
    public synchronized boolean blocked(String email, String address, long now) {
        return count("e:" + normalize(email), now) >= perEmail || count("a:" + address, now) >= perAddress;
    }

    public synchronized void recordFailure(String email, String address, long now) {
        if (failures.size() > MAX_KEYS) {
            failures.values().removeIf(d -> d.isEmpty() || now - d.peekLast() >= windowMillis);
        }
        failures.computeIfAbsent("e:" + normalize(email), k -> new ArrayDeque<>()).addLast(now);
        failures.computeIfAbsent("a:" + address, k -> new ArrayDeque<>()).addLast(now);
    }

    public synchronized void recordSuccess(String email) {
        failures.remove("e:" + normalize(email));
    }

    private int count(String key, long now) {
        Deque<Long> times = failures.get(key);
        if (times == null) {
            return 0;
        }
        while (!times.isEmpty() && now - times.peekFirst() >= windowMillis) {
            times.pollFirst();
        }
        return times.size();
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
