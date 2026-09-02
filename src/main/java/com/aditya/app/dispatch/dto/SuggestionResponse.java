package com.aditya.app.dispatch.dto;

import com.aditya.app.dispatch.domain.ReassignmentSuggestion;
import com.aditya.app.dispatch.domain.SuggestionStatus;
import com.aditya.app.dispatch.domain.TriggerReason;

import java.math.BigDecimal;
import java.time.Instant;

public record SuggestionResponse(
        Long id,
        String orderId,
        String recommendedAgentId,
        BigDecimal confidence,
        String reasoning,
        SuggestionStatus status,
        TriggerReason triggerReason,
        String strategyUsed,
        Instant createdAt
) {
    public static SuggestionResponse from(ReassignmentSuggestion suggestion) {
        return new SuggestionResponse(
                suggestion.getId(),
                suggestion.getOrderId(),
                suggestion.getRecommendedAgentId(),
                suggestion.getConfidence(),
                suggestion.getReasoning(),
                suggestion.getStatus(),
                suggestion.getTriggerReason(),
                suggestion.getStrategyUsed(),
                suggestion.getCreatedAt());
    }
}
