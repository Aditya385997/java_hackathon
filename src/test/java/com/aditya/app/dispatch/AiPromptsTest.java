package com.aditya.app.dispatch;

import com.aditya.app.dispatch.domain.AgentStatus;
import com.aditya.app.dispatch.domain.OrderStatus;
import com.aditya.app.dispatch.domain.TriggerReason;
import com.aditya.app.dispatch.routing.AgentSnapshot;
import com.aditya.app.dispatch.routing.OrderSnapshot;
import com.aditya.app.dispatch.routing.RecoveryContext;
import com.aditya.app.dispatch.routing.RoutingContext;
import com.aditya.app.dispatch.routing.ai.AiPrompts;
import com.aditya.app.dispatch.routing.ai.LlmPrompt;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiPromptsTest {

    private static final List<AgentSnapshot> ROSTER = List.of(
            new AgentSnapshot("AGT-001", "Priya Sharma", 2, AgentStatus.BUSY),
            new AgentSnapshot("AGT-002", "Rahul Verma", 0, AgentStatus.AVAILABLE),
            new AgentSnapshot("AGT-004", "Kiran Nair", 1, AgentStatus.AVAILABLE));

    private static final OrderSnapshot ORDER = new OrderSnapshot(
            "ORD-001", "Electronics — Koramangala to Indiranagar", "AGT-001", OrderStatus.ASSIGNED);

    private static RoutingContext initialContext() {
        return new RoutingContext(ORDER, ROSTER, TriggerReason.INITIAL);
    }

    private static RoutingContext recoveryContext() {
        return new RoutingContext(ORDER, ROSTER, TriggerReason.AGENT_OFFLINE,
                new RecoveryContext("AGT-001", 3));
    }

    private static String whole(LlmPrompt prompt) {
        return prompt.systemInstruction() + "\n" + prompt.userPrompt();
    }

    @Test
    void initialPromptFramesTheCallAsAManualOperatorRequest() {
        LlmPrompt prompt = AiPrompts.forContext(initialContext());

        assertThat(prompt.systemInstruction())
                .contains("routing assistant")
                .contains("Nothing has failed")
                .contains("routine optimisation");
        assertThat(prompt.userPrompt()).contains("manual, requested by an operator");
    }

    @Test
    void initialPromptCarriesTheOrderAndEveryAgentWithItsCurrentLoad() {
        String prompt = whole(AiPrompts.forContext(initialContext()));

        assertThat(prompt)
                .contains("ORD-001")
                .contains("Electronics — Koramangala to Indiranagar")
                .contains("Currently assigned to: AGT-001")
                .contains("AGT-001 | Priya Sharma | BUSY | 2 active orders")
                .contains("AGT-002 | Rahul Verma | AVAILABLE | 0 active orders")
                .contains("AGT-004 | Kiran Nair | AVAILABLE | 1 active order");
    }

    @Test
    void recoveryPromptNamesTheOfflineAgentAndTheStrandedOrderCount() {
        LlmPrompt prompt = AiPrompts.forContext(recoveryContext());

        assertThat(prompt.userPrompt())
                .contains("agent AGT-001 went OFFLINE")
                .contains("Orders stranded by that failure: 3")
                .contains("the remaining 2 still need agents")
                .contains("Affected order being re-planned now");
        assertThat(prompt.systemInstruction())
                .contains("recovery planner")
                .contains("incident recovery, not routine optimisation");
    }

    @Test
    void recoveryPromptStillWorksBeforeTheOfflineTriggerPopulatesRecoveryFacts() {
        // T-3 designs this path; T-4 is what fills RecoveryContext in. A null must not throw.
        LlmPrompt prompt = AiPrompts.forContext(
                new RoutingContext(ORDER, ROSTER, TriggerReason.AGENT_OFFLINE));

        assertThat(prompt.userPrompt()).contains("were not recorded");
        assertThat(prompt.systemInstruction()).contains("recovery planner");
    }

    @Test
    void theTwoPromptsAreMateriallyDifferentNotOneTemplateWithASwappedLabel() {
        String initial = whole(AiPrompts.forContext(initialContext()));
        String recovery = whole(AiPrompts.forContext(recoveryContext()));

        assertThat(recovery).isNotEqualTo(initial);

        // Recovery-only framing: an incident, stranded work, and a tie-break that reserves
        // capacity for the orders still waiting.
        assertThat(recovery).contains("recovery planner").contains("stranded")
                .contains("headroom to absorb more work");
        assertThat(initial).doesNotContain("recovery planner").doesNotContain("stranded")
                .doesNotContain("headroom to absorb more work");

        // Initial-only framing: no incident, routine optimisation.
        assertThat(initial).contains("Nothing has failed").contains("routine optimisation");
        assertThat(recovery).doesNotContain("Nothing has failed");
    }

    @Test
    void bothPromptsStateTheSameResponseContract() {
        for (LlmPrompt prompt : List.of(AiPrompts.forContext(initialContext()),
                AiPrompts.forContext(recoveryContext()))) {
            assertThat(prompt.userPrompt())
                    .contains("recommendedAgentId")
                    .contains("confidence")
                    .contains("reasoning")
                    .contains("Never pick an agent whose status is not AVAILABLE");
        }
    }
}
