package com.aditya.app.dispatch;

import com.aditya.app.dispatch.domain.AgentStatus;
import com.aditya.app.dispatch.domain.OrderStatus;
import com.aditya.app.dispatch.domain.TriggerReason;
import com.aditya.app.dispatch.routing.AgentSnapshot;
import com.aditya.app.dispatch.routing.OrderSnapshot;
import com.aditya.app.dispatch.routing.RoutingContext;
import com.aditya.app.dispatch.routing.RoutingRecommendation;
import com.aditya.app.dispatch.routing.RuleBasedRoutingStrategy;
import com.aditya.app.dispatch.routing.ai.AiRoutingStrategy;
import com.aditya.app.dispatch.routing.ai.LLMGateway;
import com.aditya.app.dispatch.routing.ai.LlmGatewayException;
import com.aditya.app.dispatch.routing.ai.LlmPrompt;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gateway is stubbed rather than mocked — these tests are about what the strategy does
 * with a model's answer, so the answer is just a string handed straight back. The real
 * {@link RuleBasedRoutingStrategy} is used so that a fallback is asserted against the
 * behaviour it actually falls back to.
 */
class AiRoutingStrategyTest {

    /** Seed-shaped roster: ORD-001 is held by the BUSY AGT-001. */
    private static final AgentSnapshot BUSY_HOLDER =
            new AgentSnapshot("AGT-001", "Priya Sharma", 2, AgentStatus.BUSY);
    private static final AgentSnapshot AVAILABLE_LIGHT =
            new AgentSnapshot("AGT-002", "Rahul Verma", 0, AgentStatus.AVAILABLE);
    private static final AgentSnapshot OFFLINE_AGENT =
            new AgentSnapshot("AGT-003", "Ananya Iyer", 1, AgentStatus.OFFLINE);
    private static final AgentSnapshot AVAILABLE_HEAVY =
            new AgentSnapshot("AGT-004", "Kiran Nair", 4, AgentStatus.AVAILABLE);

    private static AiRoutingStrategy strategyAnswering(String modelResponse) {
        return strategy(prompt -> modelResponse);
    }

    private static AiRoutingStrategy strategy(LLMGateway gateway) {
        return new AiRoutingStrategy(gateway, new RuleBasedRoutingStrategy(), new ObjectMapper());
    }

    private static RoutingContext context() {
        return new RoutingContext(
                new OrderSnapshot("ORD-001", "Electronics — Koramangala to Indiranagar",
                        "AGT-001", OrderStatus.ASSIGNED),
                List.of(BUSY_HOLDER, AVAILABLE_LIGHT, OFFLINE_AGENT, AVAILABLE_HEAVY),
                TriggerReason.INITIAL);
    }

    /**
     * The order's holder is AVAILABLE here, which is the only shape that can reach the
     * "already holds this order" rule: production checks status first, and every other
     * context in this class is held by a BUSY agent that trips the earlier rule instead.
     */
    private static RoutingContext contextHeldByAnAvailableAgent() {
        return new RoutingContext(
                new OrderSnapshot("ORD-002", "Groceries — HSR Layout to BTM",
                        "AGT-002", OrderStatus.ASSIGNED),
                List.of(BUSY_HOLDER, AVAILABLE_LIGHT, OFFLINE_AGENT, AVAILABLE_HEAVY),
                TriggerReason.INITIAL);
    }

    private static String answer(String agentId, String confidence, String reasoning) {
        return """
                {"recommendedAgentId":"%s","confidence":%s,"reasoning":"%s"}"""
                .formatted(agentId, confidence, reasoning);
    }

    // ── registration ────────────────────────────────────────────────────────────

    @Test
    void isRegisteredUnderTheKeyAi() {
        assertThat(strategyAnswering("{}").key()).isEqualTo("ai");
    }

    // ── the AI path ─────────────────────────────────────────────────────────────

    @Test
    void usesTheModelRecommendationWhenTheResponseIsValid() {
        // AGT-004 is the heavier of the two AVAILABLE agents, so a rule-based fallback
        // would have picked AGT-002 — this can only be the model's answer.
        List<RoutingRecommendation> recommendations = strategyAnswering(
                answer("AGT-004", "0.77", "Kiran has local knowledge of Indiranagar."))
                .recommend(context());

        assertThat(recommendations).singleElement().satisfies(recommendation -> {
            assertThat(recommendation.recommendedAgentId()).isEqualTo("AGT-004");
            assertThat(recommendation.strategyUsed()).isEqualTo("ai");
        });
    }

    @Test
    void keepsTheModelReasoningAndConfidenceExactlyAsReturned() {
        String reasoning = "Rahul is idle and closest to the pickup, so handover is immediate.";

        RoutingRecommendation recommendation = strategyAnswering(
                answer("AGT-002", "0.83", reasoning)).recommend(context()).get(0);

        assertThat(recommendation.reasoning())
                .as("the model's words are the deliverable and must not be rewritten")
                .isEqualTo(reasoning);
        assertThat(recommendation.confidence()).isEqualByComparingTo("0.83");
    }

    @Test
    void normalisesConfidenceToTheTwoDecimalPlacesThePersistedColumnHolds() {
        // NUMERIC(3,2) storage, not a change of decision: the agent chosen is unaffected.
        RoutingRecommendation recommendation = strategyAnswering(
                answer("AGT-002", "0.8259", "Lightest load on the roster."))
                .recommend(context()).get(0);

        assertThat(recommendation.confidence()).isEqualByComparingTo("0.83");
        assertThat(recommendation.confidence().scale()).isEqualTo(2);
        assertThat(recommendation.recommendedAgentId()).isEqualTo("AGT-002");
    }

    @Test
    void acceptsTheBoundaryConfidenceValues() {
        assertThat(strategyAnswering(answer("AGT-002", "0", "No better option is obvious."))
                .recommend(context()).get(0).strategyUsed()).isEqualTo("ai");
        assertThat(strategyAnswering(answer("AGT-002", "1", "Unambiguously the right agent."))
                .recommend(context()).get(0).strategyUsed()).isEqualTo("ai");
    }

    // ── fallback matrix ─────────────────────────────────────────────────────────

    @Test
    void fallsBackWhenTheGatewayFails() {
        List<RoutingRecommendation> recommendations = strategy(prompt -> {
            throw new LlmGatewayException("Gemini call failed: 429 Too Many Requests");
        }).recommend(context());

        assertThat(recommendations).isNotEmpty();
        assertThat(recommendations.get(0).recommendedAgentId()).isEqualTo("AGT-002");
    }

    @Test
    void fallsBackWhenTheResponseIsNotJson() {
        assertThatFellBack(strategyAnswering("I think Kiran would be a good choice!"));
    }

    @Test
    void fallsBackWhenRequiredFieldsAreMissing() {
        assertThatFellBack(strategyAnswering("{\"confidence\":0.9}"));
        assertThatFellBack(strategyAnswering(
                "{\"recommendedAgentId\":\"AGT-002\",\"reasoning\":\"Idle.\"}"));
    }

    @Test
    void fallsBackWhenTheRecommendedAgentWasNeverACandidate() {
        assertThatFellBack(strategyAnswering(
                answer("AGT-999", "0.95", "Deepak is the strongest performer this week.")));
    }

    @Test
    void fallsBackWhenTheRecommendedAgentIsOffline() {
        assertThatFellBack(strategyAnswering(answer("AGT-003", "0.9", "Ananya is nearby.")));
    }

    @Test
    void fallsBackWhenTheRecommendedAgentIsBusy() {
        // AGT-001 also happens to hold the order, but BUSY is what stops it: the status rule
        // runs first. The current-agent rule is covered separately, below.
        assertThatFellBack(strategyAnswering(answer("AGT-001", "0.9", "Priya already has it.")));
    }

    @Test
    void fallsBackWhenTheModelPicksTheAgentAlreadyHoldingTheOrder() {
        // AGT-002 is AVAILABLE and the lightest agent on the roster, so it clears every check
        // except the one under test. Rule-based excludes it for that same reason, which is why
        // the fallback lands on the heavier AGT-004 — proof the rule actually fired.
        List<RoutingRecommendation> recommendations = strategyAnswering(
                answer("AGT-002", "0.9", "Rahul already has it and is idle."))
                .recommend(contextHeldByAnAvailableAgent());

        assertThat(recommendations).isNotEmpty();
        assertThat(recommendations.get(0).strategyUsed()).isEqualTo("rule-based");
        assertThat(recommendations.get(0).recommendedAgentId())
                .as("an AVAILABLE agent already holding the order must still be rejected")
                .isEqualTo("AGT-004");
    }

    @Test
    void fallsBackWhenConfidenceIsOutsideTheUnitInterval() {
        assertThatFellBack(strategyAnswering(answer("AGT-002", "1.5", "Certain beyond doubt.")));
        assertThatFellBack(strategyAnswering(answer("AGT-002", "-0.1", "Reluctant guess.")));
    }

    @Test
    void fallsBackWhenReasoningIsBlank() {
        assertThatFellBack(strategyAnswering(answer("AGT-002", "0.8", "   ")));
    }

    @Test
    void fallbackRecommendationsAreLabelledRuleBasedNotAi() {
        List<RoutingRecommendation> recommendations =
                strategyAnswering("not json at all").recommend(context());

        assertThat(recommendations)
                .as("strategyUsed must name whatever actually produced the answer")
                .isNotEmpty()
                .allSatisfy(r -> assertThat(r.strategyUsed()).isEqualTo("rule-based"));
    }

    @Test
    void returnsEmptyWhenNeitherTheModelNorTheRuleHasACandidate() {
        RoutingContext noneEligible = new RoutingContext(
                new OrderSnapshot("ORD-001", "Electronics", "AGT-001", OrderStatus.ASSIGNED),
                List.of(BUSY_HOLDER, OFFLINE_AGENT),
                TriggerReason.INITIAL);

        assertThat(strategyAnswering(answer("AGT-003", "0.9", "Ananya can stretch."))
                .recommend(noneEligible))
                .as("an empty list is what makes the caller refuse before mutating anything")
                .isEmpty();
    }

    // ── prompt wiring ───────────────────────────────────────────────────────────

    @Test
    void sendsThePromptThatMatchesTheTriggerReason() {
        LlmPrompt[] captured = new LlmPrompt[1];
        LLMGateway recording = prompt -> {
            captured[0] = prompt;
            return answer("AGT-002", "0.8", "Idle and close by.");
        };

        strategy(recording).recommend(context());

        assertThat(captured[0].systemInstruction()).contains("routing assistant");
    }

    private static void assertThatFellBack(AiRoutingStrategy strategy) {
        List<RoutingRecommendation> recommendations = strategy.recommend(context());

        assertThat(recommendations).isNotEmpty();
        assertThat(recommendations.get(0).strategyUsed()).isEqualTo("rule-based");
        assertThat(recommendations.get(0).recommendedAgentId()).isEqualTo("AGT-002");
    }
}
