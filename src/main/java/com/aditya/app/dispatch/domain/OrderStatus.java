package com.aditya.app.dispatch.domain;

import java.util.Set;

/**
 * Order lifecycle. There is deliberately no REASSIGNMENT_PENDING -> ASSIGNED edge:
 * rejecting a suggestion leaves the order pending so another one can be raised.
 */
public enum OrderStatus {
    ASSIGNED, REASSIGNMENT_PENDING, REASSIGNED, DELIVERED;

    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case ASSIGNED -> Set.of(REASSIGNMENT_PENDING, DELIVERED).contains(target);
            case REASSIGNMENT_PENDING -> target == REASSIGNED;
            case REASSIGNED -> target == DELIVERED;
            case DELIVERED -> false;
        };
    }
}
