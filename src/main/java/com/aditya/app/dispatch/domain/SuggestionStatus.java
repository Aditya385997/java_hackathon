package com.aditya.app.dispatch.domain;

import java.util.Set;

/** A suggestion is decided exactly once. Both outcomes are terminal. */
public enum SuggestionStatus {
    PENDING, ACCEPTED, REJECTED;

    public boolean canTransitionTo(SuggestionStatus target) {
        return switch (this) {
            case PENDING -> Set.of(ACCEPTED, REJECTED).contains(target);
            case ACCEPTED, REJECTED -> false;
        };
    }
}
