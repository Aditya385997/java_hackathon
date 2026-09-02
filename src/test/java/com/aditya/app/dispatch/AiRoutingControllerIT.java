package com.aditya.app.dispatch;

import com.aditya.app.dispatch.domain.Agent;
import com.aditya.app.dispatch.domain.AgentStatus;
import com.aditya.app.dispatch.domain.Order;
import com.aditya.app.dispatch.domain.OrderStatus;
import com.aditya.app.dispatch.repo.AgentRepository;
import com.aditya.app.dispatch.repo.OrderRepository;
import com.aditya.app.dispatch.repo.ReassignmentSuggestionRepository;
import com.aditya.app.dispatch.routing.RoutingStrategySelector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Two kinds of state to undo: @Transactional rolls back the seeded H2 rows, and the
 * @AfterEach restores the active strategy, which is process-wide in-memory state a rollback
 * cannot touch — the same reason {@link RoutingControllerIT} carries that hook.
 *
 * <p>No API key is configured under test, so the AI strategy is exercised through its
 * fallback path. That is deliberate: it is the path a quota error or an outage takes in
 * production, and it means `mvnw verify` never depends on a live provider.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AiRoutingControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoutingStrategySelector selector;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private ReassignmentSuggestionRepository suggestionRepository;

    @AfterEach
    void restoreDefaultStrategy() {
        selector.activate("rule-based");
    }

    @Test
    void aiIsRegisteredAlongsideRuleBased() throws Exception {
        mockMvc.perform(get("/api/v1/routing/strategy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available",
                        org.hamcrest.Matchers.hasItems("ai", "rule-based")));

        assertThat(selector.availableKeys()).contains("ai");
    }

    @Test
    void switchesRuleBasedToAiAndBackWithoutARestart() throws Exception {
        mockMvc.perform(patch("/api/v1/routing/strategy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"strategy\":\"ai\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value("ai"));
        assertThat(selector.activeKey()).isEqualTo("ai");

        mockMvc.perform(patch("/api/v1/routing/strategy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"strategy\":\"rule-based\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value("rule-based"));
        assertThat(selector.activeKey()).isEqualTo("rule-based");
    }

    @Test
    void suggestUnderAiFallsBackToRuleBasedWhenNoCredentialIsConfigured() throws Exception {
        selector.activate("ai");

        // ORD-001 is held by AGT-001; AGT-002 and AGT-004 are AVAILABLE at load 0, so the
        // rule-based tie-break on agent id picks AGT-002.
        mockMvc.perform(post("/api/v1/orders/ORD-001/suggest"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value("ORD-001"))
                .andExpect(jsonPath("$.recommendedAgentId").value("AGT-002"))
                .andExpect(jsonPath("$.strategyUsed").value("rule-based"))
                .andExpect(jsonPath("$.triggerReason").value("INITIAL"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.reasoning").isNotEmpty());
    }

    /**
     * The one path where routing produces nothing at all: the AI call fails for want of a
     * credential, and the rule-based fallback finds no eligible agent either. The request
     * must be refused with every row exactly as it was.
     */
    @Test
    void aFailedAiCallWithNoEligibleAgentLeavesEveryRowUntouched() throws Exception {
        selector.activate("ai");

        // ORD-001 is held by the BUSY AGT-001. Taking the only two AVAILABLE agents out of
        // the running leaves the rule-based fallback with an empty candidate list.
        markBusy("AGT-002");
        markBusy("AGT-004");
        long suggestionsBefore = suggestionRepository.count();
        int holderLoadBefore = loadOf("AGT-001");

        mockMvc.perform(post("/api/v1/orders/ORD-001/suggest"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message",
                        org.hamcrest.Matchers.containsString("No eligible agent available")));

        Order order = orderRepository.findById("ORD-001").orElseThrow();
        assertThat(order.getStatus())
                .as("the order must not have been parked for a reassignment that never happened")
                .isEqualTo(OrderStatus.ASSIGNED);
        assertThat(order.getAssignedAgentId()).isEqualTo("AGT-001");
        assertThat(suggestionRepository.count())
                .as("no suggestion may be persisted when routing produced nothing")
                .isEqualTo(suggestionsBefore);
        assertThat(loadOf("AGT-001"))
                .as("no agent load may move")
                .isEqualTo(holderLoadBefore);
    }

    private void markBusy(String agentId) {
        Agent agent = agentRepository.findById(agentId).orElseThrow();
        agent.changeStatus(AgentStatus.BUSY);
        agentRepository.save(agent);
    }

    private int loadOf(String agentId) {
        return agentRepository.findById(agentId).orElseThrow().getActiveOrderCount();
    }
}
