package com.aditya.app.dispatch.domain;

import com.aditya.app.common.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * An advisory record: "order X should move to agent Y". Holds ids rather than
 * associations — see NOTES.md.
 */
@Entity
@Table(name = "reassignment_suggestions")
public class ReassignmentSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, length = 32)
    private String orderId;

    @Column(name = "recommended_agent_id", nullable = false, length = 32)
    private String recommendedAgentId;

    @Column(precision = 3, scale = 2)
    private BigDecimal confidence;

    @Column(columnDefinition = "TEXT")
    private String reasoning;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SuggestionStatus status = SuggestionStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_reason", length = 32)
    private TriggerReason triggerReason;

    @Column(name = "strategy_used", length = 64)
    private String strategyUsed;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected ReassignmentSuggestion() { /* for JPA */ }

    public ReassignmentSuggestion(String orderId, String recommendedAgentId, BigDecimal confidence,
                                  String reasoning, TriggerReason triggerReason, String strategyUsed) {
        this.orderId = orderId;
        this.recommendedAgentId = recommendedAgentId;
        this.confidence = confidence;
        this.reasoning = reasoning;
        this.triggerReason = triggerReason;
        this.strategyUsed = strategyUsed;
    }

    /**
     * Guard without applying, so a caller can reject a decided suggestion before it starts
     * moving order and agent state around.
     */
    public void requireDecidable(SuggestionStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new BusinessRuleException(
                    "Suggestion " + id + " is already " + status + " and cannot become " + target);
        }
    }

    public void decide(SuggestionStatus target) {
        requireDecidable(target);
        this.status = target;
    }

    public Long getId() { return id; }
    public String getOrderId() { return orderId; }
    public String getRecommendedAgentId() { return recommendedAgentId; }
    public BigDecimal getConfidence() { return confidence; }
    public String getReasoning() { return reasoning; }
    public SuggestionStatus getStatus() { return status; }
    public TriggerReason getTriggerReason() { return triggerReason; }
    public String getStrategyUsed() { return strategyUsed; }
    public Instant getCreatedAt() { return createdAt; }
}
