package com.example.EPustok.market;

import static com.example.EPustok.market.ExchangeState.ACCEPTED;
import static com.example.EPustok.market.ExchangeState.PROPOSED;
import static com.example.EPustok.market.ExchangeState.REJECTED;
import static com.example.EPustok.market.ExchangeState.WITHDRAWN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExchangeStateTest {

    @Test
    void proposedMovesToAcceptedRejectedOrWithdrawn() {
        assertTrue(PROPOSED.canTransitionTo(ACCEPTED));
        assertTrue(PROPOSED.canTransitionTo(REJECTED));
        assertTrue(PROPOSED.canTransitionTo(WITHDRAWN));
    }

    @Test
    void acceptedIsTerminal() {
        for (ExchangeState to : ExchangeState.values()) {
            assertFalse(ACCEPTED.canTransitionTo(to), "ACCEPTED -> " + to + " must be illegal");
        }
    }

    @Test
    void rejectedIsTerminal() {
        for (ExchangeState to : ExchangeState.values()) {
            assertFalse(REJECTED.canTransitionTo(to), "REJECTED -> " + to + " must be illegal");
        }
    }

    @Test
    void withdrawnIsTerminal() {
        for (ExchangeState to : ExchangeState.values()) {
            assertFalse(WITHDRAWN.canTransitionTo(to), "WITHDRAWN -> " + to + " must be illegal");
        }
    }

    @Test
    void fullTransitionTableMatchesTheSpec() {
        Map<ExchangeState, Set<ExchangeState>> legal = Map.of(
                PROPOSED, Set.of(ACCEPTED, REJECTED, WITHDRAWN),
                ACCEPTED, Set.of(),
                REJECTED, Set.of(),
                WITHDRAWN, Set.of());
        for (ExchangeState from : ExchangeState.values()) {
            for (ExchangeState to : ExchangeState.values()) {
                assertEquals(legal.get(from).contains(to), from.canTransitionTo(to), from + " -> " + to);
            }
        }
    }
}
