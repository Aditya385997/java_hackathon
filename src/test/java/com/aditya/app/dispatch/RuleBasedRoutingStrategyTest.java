package com.aditya.app.dispatch;

import com.aditya.app.dispatch.domain.AgentStatus;
import com.aditya.app.dispatch.domain.OrderStatus;
import com.aditya.app.dispatch.domain.TriggerReason;
import com.aditya.app.dispatch.routing.AgentSnapshot;
import com.aditya.app.dispatch.routing.OrderSnapshot;
import com.aditya.app.dispatch.routing.RoutingContext;
import com.aditya.app.dispatch.routing.RoutingRecommendation;
import com.aditya.app.dispatch.routing.RuleBasedRoutingStrategy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedRoutingStrategyTest {

    private final RuleBasedRoutingStrategy strategy = new RuleBasedRoutingStrategy();

    private static AgentSnapshot agent(String id, int load, AgentStatus status) {
        return new AgentSnapshot(id, "Agent " + id, load, status);
    }

    private static RoutingContext contextFor(List<AgentSnapshot> agents) {
        return new RoutingContext(
                new OrderSnapshot("ORD-001", "Electronics", "AGT-001", OrderStatus.ASSIGNED),
                agents, TriggerReason.INITIAL);
    }

    private List<String> recommendedIds(List<AgentSnapshot> agents) {
        return strategy.recommend(contextFor(agents)).stream()
                .map(RoutingRecommendation::recommendedAgentId)
                .toList();
    }

    @Test
    void ranksLeastLoadedAvailableAgentFirst() {
        assertThat(recommendedIds(List.of(
                agent("AGT-005", 3, AgentStatus.AVAILABLE),
                agent("AGT-002", 0, AgentStatus.AVAILABLE),
                agent("AGT-004", 1, AgentStatus.AVAILABLE))))
                .containsExactly("AGT-002", "AGT-004", "AGT-005");
    }

    @Test
    void excludesBusyAgents() {
        assertThat(recommendedIds(List.of(
                agent("AGT-003", 0, AgentStatus.BUSY),
                agent("AGT-002", 2, AgentStatus.AVAILABLE))))
                .containsExactly("AGT-002");
    }

    @Test
    void excludesOfflineAgents() {
        assertThat(recommendedIds(List.of(
                agent("AGT-003", 0, AgentStatus.OFFLINE),
                agent("AGT-002", 2, AgentStatus.AVAILABLE))))
                .containsExactly("AGT-002");
    }

    @Test
    void excludesTheCurrentlyAssignedAgent() {
        assertThat(recommendedIds(List.of(
                agent("AGT-001", 0, AgentStatus.AVAILABLE),
                agent("AGT-002", 5, AgentStatus.AVAILABLE))))
                .as("AGT-001 already holds the order, so it is not a reassignment target")
                .containsExactly("AGT-002");
    }

    @Test
    void breaksLoadTiesByAgentIdAscending() {
        assertThat(recommendedIds(List.of(
                agent("AGT-004", 0, AgentStatus.AVAILABLE),
                agent("AGT-002", 0, AgentStatus.AVAILABLE))))
                .containsExactly("AGT-002", "AGT-004");
    }

    @Test
    void returnsEveryEligibleAgentOrderedNotJustTheWinner() {
        List<RoutingRecommendation> recommendations = strategy.recommend(contextFor(List.of(
                agent("AGT-002", 0, AgentStatus.AVAILABLE),
                agent("AGT-004", 1, AgentStatus.AVAILABLE),
                agent("AGT-005", 2, AgentStatus.AVAILABLE))));

        assertThat(recommendations).hasSize(3);
        assertThat(recommendations).allSatisfy(recommendation -> {
            assertThat(recommendation.strategyUsed()).isEqualTo("rule-based");
            assertThat(recommendation.confidence()).isEqualByComparingTo(new BigDecimal("1.00"));
            assertThat(recommendation.reasoning()).isNotBlank();
        });
    }

    @Test
    void returnsEmptyWhenNoAgentIsEligible() {
        assertThat(strategy.recommend(contextFor(List.of(
                agent("AGT-001", 0, AgentStatus.AVAILABLE),
                agent("AGT-003", 0, AgentStatus.BUSY),
                agent("AGT-005", 0, AgentStatus.OFFLINE)))))
                .isEmpty();
    }

    @Test
    void returnsEmptyWhenThereAreNoAgentsAtAll() {
        assertThat(strategy.recommend(contextFor(List.of()))).isEmpty();
    }

    @Test
    void explainsRankLoadAndTieBreakInPlainEnglish() {
        String reasoning = strategy.recommend(contextFor(List.of(
                agent("AGT-004", 0, AgentStatus.AVAILABLE),
                agent("AGT-002", 0, AgentStatus.AVAILABLE))))
                .get(0).reasoning();

        assertThat(reasoning)
                .contains("Ranked #1 of 2 eligible agents")
                .contains("AGT-002 is AVAILABLE with 0 active orders (lightest load)")
                .contains("Tied on load with AGT-004; ordered by agent id ascending");
    }

    @Test
    void isDeterministicAcrossRepeatedCalls() {
        List<AgentSnapshot> roster = List.of(
                agent("AGT-004", 0, AgentStatus.AVAILABLE),
                agent("AGT-002", 0, AgentStatus.AVAILABLE),
                agent("AGT-005", 0, AgentStatus.AVAILABLE));

        assertThat(recommendedIds(roster)).isEqualTo(recommendedIds(roster));
    }
}
