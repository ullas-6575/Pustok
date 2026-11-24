package com.example.EPustok.identity;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * In-memory fixed-window limiter for failed sign-ins and duplicate-email sign-ups.
 * Counters live in this process only: they reset on restart and are not shared between instances.
 */
@Component
public class AuthRateLimiter {
    static final int ACCOUNT_MAX_FAILURES = 5;
    static final int IP_MAX_FAILURES = 20;
    static final Duration WINDOW = Duration.ofMinutes(15);
    /** Upper bound on tracked keys; expired windows are swept once the map grows past this. */
    static final int MAX_TRACKED_KEYS = 10_000;

    private record Window(int count, Instant start) {}

    private final Clock clock;
    private final Duration window;
    private final int accountLimit;
    private final int ipLimit;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Autowired
    public AuthRateLimiter() {
        this(Clock.systemUTC(), WINDOW, ACCOUNT_MAX_FAILURES, IP_MAX_FAILURES);
    }

    AuthRateLimiter(Clock clock, Duration window, int accountLimit, int ipLimit) {
        this.clock = clock;
        this.window = window;
        this.accountLimit = accountLimit;
        this.ipLimit = ipLimit;
    }

    /** Blocked when the client IP or the normalized email has reached its failure limit inside the window. */
    public boolean isLoginBlocked(String ip, String email) {
        return blocked(loginIpKey(ip), ipLimit) || blocked(loginAccountKey(email), accountLimit);
    }

    public void recordLoginFailure(String ip, String email) {
        fail(loginIpKey(ip));
        fail(loginAccountKey(email));
    }

    /**
     * Clears the account counter only. The IP counter keeps running, so a valid account cannot reset
     * the limit for its own IP while guessing other accounts.
     */
    public void recordLoginSuccess(String email) {
        windows.remove(loginAccountKey(email));
    }

    public boolean isSignupBlocked(String ip) {
        return blocked(signupIpKey(ip), ipLimit);
    }

    /** Counted only for rejected duplicate emails, which is the response that reveals registrations. */
    public void recordSignupFailure(String ip) {
        fail(signupIpKey(ip));
    }

    /** Client address used as the IP key. Behind a reverse proxy this is the proxy address unless forwarded headers are configured. */
    public static String clientIp(HttpServletRequest request) {
        String address = request.getRemoteAddr();
        return address == null ? "unknown" : address;
    }

    static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /** Test hook: forget every counter. */
    void reset() {
        windows.clear();
    }

    private static String loginIpKey(String ip) { return "login-ip|" + ip; }
    private static String loginAccountKey(String email) { return "login-account|" + normalizeEmail(email); }
    private static String signupIpKey(String ip) { return "signup-ip|" + ip; }

    private boolean blocked(String key, int limit) {
        Window current = windows.get(key);
        return current != null && current.count() >= limit && clock.instant().isBefore(current.start().plus(window));
    }

    private void fail(String key) {
        Instant now = clock.instant();
        windows.compute(key, (k, current) -> current == null || !now.isBefore(current.start().plus(window))
                ? new Window(1, now)
                : new Window(current.count() + 1, current.start()));
        if (windows.size() > MAX_TRACKED_KEYS) {
            windows.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().start().plus(window)));
        }
    }
}
