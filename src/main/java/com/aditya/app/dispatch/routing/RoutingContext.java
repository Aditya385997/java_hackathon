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
 */
public record RoutingContext(OrderSnapshot order, List<AgentSnapshot> agents,
                             TriggerReason triggerReason) {}
