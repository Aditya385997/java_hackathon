package com.aditya.app.dispatch.routing.ai;

import com.aditya.app.dispatch.domain.AgentStatus;
import com.aditya.app.dispatch.routing.AgentSnapshot;
import com.aditya.app.dispatch.routing.RoutingContext;
import com.aditya.app.dispatch.routing.RoutingRecommendation;
import com.aditya.app.dispatch.routing.RoutingStrategy;
import com.aditya.app.dispatch.routing.RuleBasedRoutingStrategy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * Asks a language model where an order should go, and refuses to believe it without checking.
 *
 * <p>The model's answer is untrusted input. It is accepted only if it parses, names an agent
 * that was actually offered as a candidate, that agent is AVAILABLE and is not the one
 * already holding the order, its confidence is a real probability and its reasoning is not
 * empty. Anything else — including a timeout, a quota error or a missing credential — falls
 * back to {@link RuleBasedRoutingStrategy} with the reason logged.
 *
 * <p>Stateless and therefore safe to call from several threads at once, which is what lets
 * T-4's background offline handler reuse this same bean.
 */
@Component
public class AiRoutingStrategy implements RoutingStrategy {

    public static final String KEY = "ai";

    private static final Logger log = LoggerFactory.getLogger(AiRoutingStrategy.class);

    /**
     * The column behind {@code ReassignmentSuggestion.confidence} is NUMERIC(3,2). Rounding
     * happens only after the raw model value has been range-checked, so this is persistence
     * normalisation — it never turns an invalid confidence into an acceptable one, and it
     * never changes which agent was chosen.
     */
    private static final int CONFIDENCE_SCALE = 2;

    private final LLMGateway gateway;
    private final RuleBasedRoutingStrategy ruleBased;
    private final ObjectMapper objectMapper;

    /**
     * Depends on the concrete rule-based strategy rather than the registry: the registry is
     * built from every {@code RoutingStrategy} bean, this one included, so asking it for the
     * fallback would be a circular dependency.
     */
    public AiRoutingStrategy(LLMGateway gateway, RuleBasedRoutingStrategy ruleBased,
                             ObjectMapper objectMapper) {
        this.gateway = gateway;
        this.ruleBased = ruleBased;
        this.objectMapper = objectMapper;
    }

    @Override
    public String key() {
        return KEY;
    }

    /**
     * One candidate on success — a model asked for its best answer gives one, and the caller
     * only ever persists the top of the list. On any failure the rule-based ranking is
     * returned untouched, so what comes back is already labelled {@code "rule-based"}.
     */
    @Override
    public List<RoutingRecommendation> recommend(RoutingContext context) {
        String rejection;
        try {
            AiRecommendation answer = objectMapper.readValue(
                    gateway.complete(AiPrompts.forContext(context)), AiRecommendation.class);

            rejection = firstViolation(answer, context);
            if (rejection == null) {
                log.info("AI recommended agent {} for order {} with confidence {}",
                        answer.recommendedAgentId(), context.order().id(), answer.confidence());
                return List.of(new RoutingRecommendation(
                        answer.recommendedAgentId(),
                        answer.confidence().setScale(CONFIDENCE_SCALE, RoundingMode.HALF_UP),
                        answer.reasoning(),   // verbatim: the model's words are the deliverable
                        KEY));
            }
        } catch (LlmGatewayException e) {
            rejection = e.getMessage();
        } catch (JsonProcessingException e) {
            rejection = "response was not parseable JSON: " + e.getOriginalMessage();
        }

        log.warn("AI routing fell back to rule-based for order {}: {}",
                context.order().id(), rejection);
        return ruleBased.recommend(context);
    }

    /**
     * @return the first reason this answer cannot be used, or null when every check passes.
     *         Checks run against the raw model values, before any normalisation.
     */
    private String firstViolation(AiRecommendation answer, RoutingContext context) {
        if (answer == null) {
            return "response body was empty";
        }
        String agentId = answer.recommendedAgentId();
        if (agentId == null || agentId.isBlank()) {
            return "recommendedAgentId is missing";
        }

        AgentSnapshot chosen = context.agents().stream()
                .filter(agent -> agentId.equals(agent.id()))
                .findFirst()
                .orElse(null);
        if (chosen == null) {
            return "agent '" + agentId + "' was never offered as a candidate";
        }
        if (chosen.status() != AgentStatus.AVAILABLE) {
            return "agent " + agentId + " is " + chosen.status() + ", not AVAILABLE";
        }
        if (Objects.equals(agentId, context.order().assignedAgentId())) {
            return "agent " + agentId + " already holds order " + context.order().id();
        }

        BigDecimal confidence = answer.confidence();
        if (confidence == null) {
            return "confidence is missing";
        }
        if (confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
            return "confidence " + confidence + " is outside [0,1]";
        }
        if (answer.reasoning() == null || answer.reasoning().isBlank()) {
            return "reasoning is blank";
        }
        return null;
    }
}
