package com.example.EPustok.market;

/**
 * Lifecycle of an exchange offer. Only the transitions below are legal; ACCEPTED, REJECTED and WITHDRAWN are terminal.
 * Who may perform each transition is decided by MarketService, not here.
 */
public enum ExchangeState {
    PROPOSED, ACCEPTED, REJECTED, WITHDRAWN;

    public boolean canTransitionTo(ExchangeState next) {
        return switch (this) {
            case PROPOSED -> next == ACCEPTED || next == REJECTED || next == WITHDRAWN;
            case ACCEPTED, REJECTED, WITHDRAWN -> false;
        };
    }
}
