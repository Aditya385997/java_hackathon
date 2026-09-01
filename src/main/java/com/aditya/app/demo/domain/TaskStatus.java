package com.aditya.app.demo.domain;

import java.util.Set;

/** Reference state machine. Copy this shape for real domain workflows. */
public enum TaskStatus {
    NEW, IN_PROGRESS, DONE, CANCELLED;

    public boolean canTransitionTo(TaskStatus target) {
        return switch (this) {
            case NEW -> Set.of(IN_PROGRESS, CANCELLED).contains(target);
            case IN_PROGRESS -> Set.of(DONE, CANCELLED).contains(target);
            case DONE, CANCELLED -> false;
        };
    }
}
