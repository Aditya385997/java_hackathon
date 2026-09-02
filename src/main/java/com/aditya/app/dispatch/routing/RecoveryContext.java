package com.aditya.app.dispatch.routing;

/**
 * The extra facts an AGENT_OFFLINE re-plan needs and an INITIAL recommendation does not:
 * which agent failed, and how many of its orders are stranded behind it.
 *
 * <p>Null on a {@link RoutingContext} whose trigger is INITIAL. T-3 designs and tests this
 * seam; T-4's offline handler is what will populate it, without {@link RoutingStrategy}
 * changing shape.
 */
public record RecoveryContext(String failedAgentId, int strandedOrderCount) {}
