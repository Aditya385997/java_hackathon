package com.aditya.app.dispatch;

import com.aditya.app.dispatch.domain.Order;
import com.aditya.app.dispatch.domain.OrderStatus;
import com.aditya.app.dispatch.domain.ReassignmentSuggestion;
import com.aditya.app.dispatch.domain.SuggestionStatus;
import com.aditya.app.dispatch.domain.TriggerReason;
import com.aditya.app.dispatch.repo.AgentRepository;
import com.aditya.app.dispatch.repo.OrderRepository;
import com.aditya.app.dispatch.repo.ReassignmentSuggestionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Nothing creates suggestions in T-1 (that is T-2), so each test seeds its own through the
 * repository. @Transactional rolls the changes back afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SuggestionControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReassignmentSuggestionRepository suggestionRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private AgentRepository agentRepository;

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    /** Puts ORD-001 (held by AGT-001) into REASSIGNMENT_PENDING with a suggestion for AGT-004. */
    private ReassignmentSuggestion seedPendingReassignment() {
        Order order = orderRepository.findById("ORD-001").orElseThrow();
        order.transitionTo(OrderStatus.REASSIGNMENT_PENDING);
        orderRepository.save(order);
        return suggestionRepository.save(new ReassignmentSuggestion(
                "ORD-001", "AGT-004", new BigDecimal("0.87"),
                "AGT-001 went offline; AGT-004 is idle and nearest",
                TriggerReason.AGENT_OFFLINE, "TEST_FIXTURE"));
    }

    @Test
    void acceptingSuggestionReassignsOrderAndMovesLoadCounters() throws Exception {
        ReassignmentSuggestion suggestion = seedPendingReassignment();

        mockMvc.perform(patch("/api/v1/suggestions/" + suggestion.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "ACCEPTED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.orderId").value("ORD-001"));

        assertThat(orderRepository.findById("ORD-001")).get()
                .satisfies(order -> {
                    assertThat(order.getStatus()).isEqualTo(OrderStatus.REASSIGNED);
                    assertThat(order.getAssignedAgentId()).isEqualTo("AGT-004");
                });
        assertThat(agentRepository.findById("AGT-001").orElseThrow().getActiveOrderCount())
                .as("previous agent releases the order").isEqualTo(1);
        assertThat(agentRepository.findById("AGT-004").orElseThrow().getActiveOrderCount())
                .as("recommended agent picks it up").isEqualTo(1);
    }

    @Test
    void rejectingSuggestionLeavesOrderReassignmentPending() throws Exception {
        ReassignmentSuggestion suggestion = seedPendingReassignment();

        mockMvc.perform(patch("/api/v1/suggestions/" + suggestion.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "REJECTED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        assertThat(orderRepository.findById("ORD-001")).get()
                .satisfies(order -> {
                    assertThat(order.getStatus()).isEqualTo(OrderStatus.REASSIGNMENT_PENDING);
                    assertThat(order.getAssignedAgentId()).isEqualTo("AGT-001");
                });
        assertThat(agentRepository.findById("AGT-001").orElseThrow().getActiveOrderCount())
                .isEqualTo(2);
        assertThat(agentRepository.findById("AGT-004").orElseThrow().getActiveOrderCount())
                .isZero();
    }

    @Test
    void rejectsSecondDecisionOnTerminalSuggestionWithConflict() throws Exception {
        ReassignmentSuggestion suggestion = seedPendingReassignment();
        String body = json(Map.of("status", "ACCEPTED"));

        mockMvc.perform(patch("/api/v1/suggestions/" + suggestion.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/suggestions/" + suggestion.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void rejectsAcceptWhenOrderIsStillAssigned() throws Exception {
        ReassignmentSuggestion suggestion = suggestionRepository.save(new ReassignmentSuggestion(
                "ORD-002", "AGT-004", new BigDecimal("0.50"), "Speculative",
                TriggerReason.INITIAL, "TEST_FIXTURE"));

        mockMvc.perform(patch("/api/v1/suggestions/" + suggestion.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "ACCEPTED"))))
                .andExpect(status().isConflict());

        assertThat(orderRepository.findById("ORD-002").orElseThrow().getStatus())
                .isEqualTo(OrderStatus.ASSIGNED);
    }

    @Test
    void returnsNotFoundForUnknownSuggestion() throws Exception {
        mockMvc.perform(patch("/api/v1/suggestions/999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "ACCEPTED"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
