package com.example.EPustok.identity;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RevokedTokenCleanupTest {
    @Test void purgeDeletesOnlyRowsWhoseTokensAlreadyExpired() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        new RevokedTokenCleanup(jdbc).purgeExpired();
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture());
        assertEquals("delete from identity_revoked_tokens where expires_at < now()", sql.getValue());
    }
}
