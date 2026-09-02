package com.aditya.app.dispatch.routing;

import java.math.BigDecimal;

/**
 * One ranked candidate. Carries everything needed to persist a ReassignmentSuggestion,
 * so the orchestration layer never has to ask the strategy a follow-up question.
 */
public record RoutingRecommendation(
        String recommendedAgentId,
        BigDecimal confidence,
        String reasoning,
        String strategyUsed
) {}
