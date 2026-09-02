package com.aditya.app.dispatch.routing;

import com.aditya.app.dispatch.domain.AgentStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Ranks AVAILABLE agents by current load, lightest first, breaking ties on agent id so the
 * same roster always produces the same order.
 */
@Component
public class RuleBasedRoutingStrategy implements RoutingStrategy {

    public static final String KEY = "rule-based";

    /**
     * Fixed for every rule-based recommendation: the strategy is certain about the ordering
     * its rule produced, which is not a claim that the agent is objectively the best choice.
     * A deterministic rule carries no probability to report — deriving one from list position
     * would be arbitrary. Genuine model-returned confidence arrives with the AI strategy.
     */
    private static final BigDecimal RULE_CONFIDENCE = new BigDecimal("1.00");

    private static final Comparator<AgentSnapshot> BY_LOAD_THEN_ID =
            Comparator.comparingInt(AgentSnapshot::activeOrderCount)
                    .thenComparing(AgentSnapshot::id);

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public List<RoutingRecommendation> recommend(RoutingContext context) {
        List<AgentSnapshot> eligible = context.agents().stream()
                .filter(agent -> agent.status() == AgentStatus.AVAILABLE)
                .filter(agent -> !Objects.equals(agent.id(), context.order().assignedAgentId()))
                .sorted(BY_LOAD_THEN_ID)
                .toList();

        return eligible.stream()
                .map(agent -> new RoutingRecommendation(
                        agent.id(), RULE_CONFIDENCE, reasoningFor(agent, eligible), KEY))
                .toList();
    }

    private String reasoningFor(AgentSnapshot agent, List<AgentSnapshot> eligible) {
        int rank = eligible.indexOf(agent) + 1;
        StringBuilder reasoning = new StringBuilder()
                .append("Ranked #").append(rank).append(" of ").append(eligible.size())
                .append(eligible.size() == 1 ? " eligible agent. " : " eligible agents. ")
                .append(agent.id()).append(" is AVAILABLE with ")
                .append(agent.activeOrderCount())
                .append(agent.activeOrderCount() == 1 ? " active order" : " active orders")
                .append(rank == 1 ? " (lightest load)." : ".");

        List<String> tiedWith = eligible.stream()
                .filter(other -> other.activeOrderCount() == agent.activeOrderCount())
                .map(AgentSnapshot::id)
                .filter(id -> !id.equals(agent.id()))
                .toList();
        if (!tiedWith.isEmpty()) {
            reasoning.append(" Tied on load with ").append(String.join(", ", tiedWith))
                    .append("; ordered by agent id ascending.");
        }
        return reasoning.toString();
    }
}
