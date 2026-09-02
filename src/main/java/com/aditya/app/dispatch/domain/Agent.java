package com.aditya.app.dispatch.domain;

import com.aditya.app.common.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "agents")
public class Agent {

    @Id
    @Column(length = 32)
    private String id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "active_order_count", nullable = false)
    private int activeOrderCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AgentStatus status = AgentStatus.AVAILABLE;

    /** Sprint-2 placeholder. Nothing reads this yet. */
    @Column(length = 64)
    private String zone;

    /** Sprint-2 placeholder. Nothing reads this yet. */
    @Column(name = "max_capacity")
    private Integer maxCapacity;

    protected Agent() { /* for JPA */ }

    public Agent(String id, String name, AgentStatus status) {
        this.id = id;
        this.name = name;
        this.status = status;
    }

    public void incrementLoad() {
        activeOrderCount++;
    }

    public void decrementLoad() {
        if (activeOrderCount == 0) {
            throw new BusinessRuleException("Agent " + id + " has no active orders to release");
        }
        activeOrderCount--;
    }

    /** T-1 permits any status change. Reacting to OFFLINE is T-4. */
    public void changeStatus(AgentStatus target) {
        this.status = target;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getActiveOrderCount() { return activeOrderCount; }
    public AgentStatus getStatus() { return status; }
    public String getZone() { return zone; }
    public Integer getMaxCapacity() { return maxCapacity; }
}
