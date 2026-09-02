package com.aditya.app.dispatch.routing;

import java.util.List;

/**
 * Recommends where an order should go. Implementations recommend only: they must not touch
 * repositories, and the context gives them nothing to mutate.
 *
 * <p>Every implementation is a Spring bean; {@link RoutingStrategyRegistry} discovers them
 * by injection, so adding a strategy never means editing a caller.
 */
public interface RoutingStrategy {

    /** Stable key used to select this strategy at runtime, e.g. {@code "rule-based"}. */
    String key();

    /** Candidates best-first. Empty when nothing is eligible — never null. */
    List<RoutingRecommendation> recommend(RoutingContext context);
}
