package com.aditya.app.dispatch.routing;

import com.aditya.app.dispatch.domain.Order;
import com.aditya.app.dispatch.domain.OrderStatus;

/**
 * Read-only view of the order being routed. Sprint-2 fields (zone, weightClass, slaDeadline)
 * join this record when a strategy actually reads them.
 */
public record OrderSnapshot(String id, String description, String assignedAgentId,
                            OrderStatus status) {

    public static OrderSnapshot from(Order order) {
        return new OrderSnapshot(order.getId(), order.getDescription(),
                order.getAssignedAgentId(), order.getStatus());
    }
}
