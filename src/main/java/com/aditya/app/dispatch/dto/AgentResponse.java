package com.aditya.app.dispatch.dto;

import com.aditya.app.dispatch.domain.Agent;
import com.aditya.app.dispatch.domain.AgentStatus;

public record AgentResponse(
        String id,
        String name,
        int activeOrderCount,
        AgentStatus status,
        String zone,
        Integer maxCapacity
) {
    public static AgentResponse from(Agent agent) {
        return new AgentResponse(
                agent.getId(),
                agent.getName(),
                agent.getActiveOrderCount(),
                agent.getStatus(),
                agent.getZone(),
                agent.getMaxCapacity());
    }
}
