package com.aditya.app.dispatch.dto;

import com.aditya.app.dispatch.domain.Order;
import com.aditya.app.dispatch.domain.OrderStatus;

import java.time.Instant;

public record OrderResponse(
        String id,
        String description,
        String assignedAgentId,
        OrderStatus status,
        Instant createdAt,
        String zone,
        String weightClass,
        Instant slaDeadline
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getDescription(),
                order.getAssignedAgentId(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getZone(),
                order.getWeightClass(),
                order.getSlaDeadline());
    }
}
