package com.example.EPustok.identity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuthRateLimiterTest {
    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final AuthRateLimiter limiter = new AuthRateLimiter(clock, Duration.ofMinutes(15), 5, 20);

    @Test void accountIsBlockedAfterFailuresAndOtherAccountsOnSameIpAreNot() {
        for (int i = 0; i < 4; i++) limiter.recordLoginFailure("1.1.1.1", "a@example.com");
        assertFalse(limiter.isLoginBlocked("1.1.1.1", "a@example.com"));
        limiter.recordLoginFailure("1.1.1.1", "a@example.com");
        assertTrue(limiter.isLoginBlocked("1.1.1.1", "a@example.com"));
        assertTrue(limiter.isLoginBlocked("2.2.2.2", "a@example.com"), "the account limit follows the email across IPs");
        assertFalse(limiter.isLoginBlocked("1.1.1.1", "b@example.com"));
    }

    @Test void emailIsNormalizedBeforeCounting() {
        for (int i = 0; i < 5; i++) limiter.recordLoginFailure("1.1.1.1", "  Mixed@Example.COM ");
        assertTrue(limiter.isLoginBlocked("9.9.9.9", "mixed@example.com"));
    }

    @Test void ipLimitBlocksSprayingAcrossAccounts() {
        for (int i = 0; i < 19; i++) limiter.recordLoginFailure("3.3.3.3", "user" + i + "@example.com");
        assertFalse(limiter.isLoginBlocked("3.3.3.3", "fresh@example.com"));
        limiter.recordLoginFailure("3.3.3.3", "user19@example.com");
        assertTrue(limiter.isLoginBlocked("3.3.3.3", "fresh@example.com"));
        assertFalse(limiter.isLoginBlocked("4.4.4.4", "fresh@example.com"));
    }

    @Test void blockExpiresWhenWindowPasses() {
        for (int i = 0; i < 5; i++) limiter.recordLoginFailure("1.1.1.1", "a@example.com");
        assertTrue(limiter.isLoginBlocked("1.1.1.1", "a@example.com"));
        clock.advance(Duration.ofMinutes(14));
        assertTrue(limiter.isLoginBlocked("1.1.1.1", "a@example.com"));
        clock.advance(Duration.ofMinutes(1));
        assertFalse(limiter.isLoginBlocked("1.1.1.1", "a@example.com"));
    }

    @Test void successClearsTheAccountCounter() {
        for (int i = 0; i < 4; i++) limiter.recordLoginFailure("1.1.1.1", "a@example.com");
        limiter.recordLoginSuccess("a@example.com");
        for (int i = 0; i < 4; i++) limiter.recordLoginFailure("1.1.1.1", "a@example.com");
        assertFalse(limiter.isLoginBlocked("1.1.1.1", "a@example.com"));
    }

    @Test void successDoesNotResetTheIpCounter() {
        for (int i = 0; i < 20; i++) limiter.recordLoginFailure("5.5.5.5", "user" + i + "@example.com");
        limiter.recordLoginSuccess("user0@example.com");
        assertTrue(limiter.isLoginBlocked("5.5.5.5", "fresh@example.com"));
    }

    @Test void signupLimitIsSeparateFromLoginLimit() {
        for (int i = 0; i < 20; i++) limiter.recordSignupFailure("6.6.6.6");
        assertTrue(limiter.isSignupBlocked("6.6.6.6"));
        assertFalse(limiter.isLoginBlocked("6.6.6.6", "a@example.com"));
        assertFalse(limiter.isSignupBlocked("7.7.7.7"));
    }

    /** Fixed-instant clock that tests advance explicitly. */
    static final class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant start) { now = start; }
        void advance(Duration d) { now = now.plus(d); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
