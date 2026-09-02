package com.aditya.app.dispatch.routing;

import com.aditya.app.dispatch.domain.TriggerReason;

import java.util.List;

/**
 * Everything a strategy is allowed to see. {@code agents} is the full roster, not a
 * pre-filtered shortlist: each strategy decides its own eligibility, so a future
 * zone- or capacity-aware strategy can weigh agents this one would discard.
 *
 * <p>{@code triggerReason} is what lets a strategy tell an initial recommendation from
 * recovery after an agent went offline, without the interface changing shape.
 *
 * <p>{@code recovery} carries the AGENT_OFFLINE-only facts and is null for INITIAL. The
 * three-argument constructor is the INITIAL form, so every T-2 caller reads unchanged.
 */
public record RoutingContext(OrderSnapshot order, List<AgentSnapshot> agents,
                             TriggerReason triggerReason, RecoveryContext recovery) {

    /** INITIAL routing carries no recovery facts. */
    public RoutingContext(OrderSnapshot order, List<AgentSnapshot> agents,
                          TriggerReason triggerReason) {
        this(order, agents, triggerReason, null);
    }
}
