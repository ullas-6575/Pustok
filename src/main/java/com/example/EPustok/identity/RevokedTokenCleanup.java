package com.example.EPustok.identity;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Deletes revocation rows once the token they revoke would have expired anyway. */
@Component
@EnableScheduling
public class RevokedTokenCleanup {
    private final JdbcTemplate jdbc;

    public RevokedTokenCleanup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Daily at 03:17 in the server time zone. */
    @Scheduled(cron = "0 17 3 * * *")
    public void purgeExpired() {
        jdbc.update("delete from identity_revoked_tokens where expires_at < now()");
    }
}
