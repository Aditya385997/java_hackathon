package com.aditya.app.dispatch.domain;

import com.aditya.app.common.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Table is explicitly "orders" — the derived name "order" is a reserved word. */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @Column(length = 32)
    private String id;

    @Column(nullable = false)
    private String description;

    @Column(name = "assigned_agent_id", length = 32)
    private String assignedAgentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status = OrderStatus.ASSIGNED;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /** Sprint-2 placeholder. Nothing reads this yet. */
    @Column(length = 64)
    private String zone;

    /** Sprint-2 placeholder. Nothing reads this yet. */
    @Column(name = "weight_class", length = 32)
    private String weightClass;

    /** Sprint-2 placeholder. Nothing reads this yet. */
    @Column(name = "sla_deadline")
    private Instant slaDeadline;

    protected Order() { /* for JPA */ }

    public Order(String id, String description, String assignedAgentId) {
        this.id = id;
        this.description = description;
        this.assignedAgentId = assignedAgentId;
    }

    /** Behaviour lives on the entity where it belongs to the entity. */
    public void transitionTo(OrderStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new BusinessRuleException(
                    "Cannot move order " + id + " from " + status + " to " + target);
        }
        this.status = target;
    }

    /**
     * Moves the order to a new agent. The transition runs first, so an order that is not
     * REASSIGNMENT_PENDING fails before any field is mutated.
     */
    public void reassignTo(String agentId) {
        transitionTo(OrderStatus.REASSIGNED);
        this.assignedAgentId = agentId;
    }

    public String getId() { return id; }
    public String getDescription() { return description; }
    public String getAssignedAgentId() { return assignedAgentId; }
    public OrderStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public String getZone() { return zone; }
    public String getWeightClass() { return weightClass; }
    public Instant getSlaDeadline() { return slaDeadline; }
}
