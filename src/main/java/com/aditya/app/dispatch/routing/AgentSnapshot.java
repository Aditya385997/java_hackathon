package com.aditya.app.dispatch.routing;

import com.aditya.app.dispatch.domain.Agent;
import com.aditya.app.dispatch.domain.AgentStatus;

/**
 * Read-only view of an agent handed to a routing strategy. A record rather than the entity
 * so a strategy has nothing it could mutate inside the caller's transaction.
 */
public record AgentSnapshot(String id, String name, int activeOrderCount, AgentStatus status) {

    public static AgentSnapshot from(Agent agent) {
        return new AgentSnapshot(agent.getId(), agent.getName(),
                agent.getActiveOrderCount(), agent.getStatus());
    }
}
