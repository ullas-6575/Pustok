package com.example.EPustok.payment;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Text check only: it reads V12__payments.sql and does not run it. The UNIQUE behaviour is not
 * exercised against a real database in this test.
 */
class PaymentMigrationTest {

    @Test void migrationDeclaresUniqueConstraintsForOrderAndIdempotencyKey() throws IOException {
        String sql;
        try (InputStream in = getClass().getResourceAsStream("/db/migration/V12__payments.sql")) {
            assertNotNull(in);
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(sql.contains("order_id UUID NOT NULL UNIQUE REFERENCES purchase_order(id)"));
        assertTrue(sql.contains("idempotency_key VARCHAR(100) NOT NULL UNIQUE"));
        assertTrue(sql.contains("amount NUMERIC(12,2) NOT NULL CHECK (amount > 0)"));
        assertTrue(sql.contains("CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED'))"));
    }
}
