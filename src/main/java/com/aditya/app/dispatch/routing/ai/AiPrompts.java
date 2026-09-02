package com.aditya.app.dispatch.routing.ai;

import com.aditya.app.dispatch.routing.AgentSnapshot;
import com.aditya.app.dispatch.routing.OrderSnapshot;
import com.aditya.app.dispatch.routing.RecoveryContext;
import com.aditya.app.dispatch.routing.RoutingContext;

/**
 * Builds the prompt for a routing call. Two prompts, not one template with the trigger
 * swapped: recovery after an agent failure is a different decision from an operator asking
 * for a better assignment, and the model is told so — different role, different framing,
 * different tie-breaker, different facts.
 *
 * <p>Stateless, so it needs no bean and unit-tests without a Spring context.
 */
public final class AiPrompts {

    /** Shared by both prompts: the wire contract the response is parsed against. */
    private static final String RESPONSE_CONTRACT = """

            Answer with a single JSON object and nothing else:
            {"recommendedAgentId": "<one agent id from the list>",
             "confidence": <number between 0 and 1>,
             "reasoning": "<plain English, two or three sentences>"}

            Rules you must not break:
            - recommendedAgentId must be copied exactly from the candidate list above.
            - Never pick an agent whose status is not AVAILABLE.
            - Never pick the agent who is already holding this order.
            - confidence is your own honest certainty, not a fixed value.""";

    private static final String INITIAL_ROLE = """
            You are a dispatch routing assistant for ZipRun, a same-day delivery network in \
            Bengaluru.

            A human operator has asked, on their own initiative, for a recommendation on where \
            one order should go. Nothing has failed and there is no incident in progress. This \
            is routine optimisation: the order is running normally and the operator simply \
            wants a better assignment than the one it has.

            Choose the single best available agent to take this order. Weigh how much work each \
            candidate is already carrying — a lighter load means faster pickup — and prefer a \
            balanced roster over piling work onto one agent. Write your reasoning for the \
            operator who will approve or reject the suggestion, so tell them what actually \
            decided it.""";

    private static final String RECOVERY_ROLE = """
            You are a dispatch recovery planner for ZipRun, a same-day delivery network in \
            Bengaluru.

            An agent has gone OFFLINE mid-shift. Their work is stranded and has to be re-planned \
            right now. This is incident recovery, not routine optimisation: the order below is \
            not being moved because a better option appeared, it is being moved because the \
            agent holding it has stopped working.

            You are re-planning ONE of that agent's stranded orders. The rest still have to go \
            somewhere too, so do not spend all the remaining slack on this one: prefer an agent \
            with genuine headroom to absorb more work over an agent who would be saturated by \
            taking it. Never route the order back to the agent who failed.

            Write your reasoning for a dispatcher working an active incident. Say what failed, \
            what you did about it, and why this agent can carry the load.""";

    private AiPrompts() { /* static factory only */ }

    /** Picks the prompt that matches why routing was asked for. */
    public static LlmPrompt forContext(RoutingContext context) {
        return switch (context.triggerReason()) {
            case INITIAL -> initial(context);
            case AGENT_OFFLINE -> agentOfflineRecovery(context);
        };
    }

    /** Manual, operator-initiated recommendation. No incident, no stranded work. */
    public static LlmPrompt initial(RoutingContext context) {
        String userPrompt = """
                Reassignment request: manual, requested by an operator.

                %s

                %s
                %s""".formatted(describeOrder(context.order()), describeAgents(context),
                RESPONSE_CONTRACT);
        return new LlmPrompt(INITIAL_ROLE, userPrompt);
    }

    /**
     * Re-planning after an agent failure. Names the failed agent and how many of its orders
     * are stranded, so the model knows this decision is one of several.
     */
    public static LlmPrompt agentOfflineRecovery(RoutingContext context) {
        RecoveryContext recovery = context.recovery();
        String incident = recovery == null
                ? "Incident: an agent went OFFLINE. The failed agent and the number of stranded "
                        + "orders were not recorded."
                : """
                Incident: agent %s went OFFLINE and can no longer deliver.
                Orders stranded by that failure: %d. The order below is one of them; the \
                remaining %d still need agents after this decision."""
                .formatted(recovery.failedAgentId(), recovery.strandedOrderCount(),
                        Math.max(recovery.strandedOrderCount() - 1, 0));

        String userPrompt = """
                %s

                Affected order being re-planned now:
                %s

                %s
                %s""".formatted(incident, describeOrder(context.order()), describeAgents(context),
                RESPONSE_CONTRACT);
        return new LlmPrompt(RECOVERY_ROLE, userPrompt);
    }

    private static String describeOrder(OrderSnapshot order) {
        return """
                Order %s
                  Description: %s
                  Currently assigned to: %s
                  Status: %s"""
                .formatted(order.id(), order.description(),
                        order.assignedAgentId() == null ? "nobody" : order.assignedAgentId(),
                        order.status());
    }

    /**
     * Every agent on the roster with its status and load, not a pre-filtered shortlist —
     * the model is told the eligibility rules and is expected to apply them, and the
     * validation boundary checks that it did.
     */
    private static String describeAgents(RoutingContext context) {
        StringBuilder agents = new StringBuilder("Candidate agents (id, name, status, orders in hand):");
        for (AgentSnapshot agent : context.agents()) {
            agents.append("\n  - ").append(agent.id())
                    .append(" | ").append(agent.name())
                    .append(" | ").append(agent.status())
                    .append(" | ").append(agent.activeOrderCount())
                    .append(agent.activeOrderCount() == 1 ? " active order" : " active orders");
        }
        return agents.toString();
    }
}
