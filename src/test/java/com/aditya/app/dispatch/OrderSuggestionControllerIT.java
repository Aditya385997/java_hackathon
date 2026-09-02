package com.aditya.app.dispatch;

import com.aditya.app.dispatch.domain.OrderStatus;
import com.aditya.app.dispatch.domain.SuggestionStatus;
import com.aditya.app.dispatch.domain.TriggerReason;
import com.aditya.app.dispatch.repo.OrderRepository;
import com.aditya.app.dispatch.repo.ReassignmentSuggestionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** @Transactional so each test rolls back the shared seeded H2 database. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderSuggestionControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ReassignmentSuggestionRepository suggestionRepository;

    @Test
    void suggestRecommendsTheLeastLoadedAvailableAgentAndParksTheOrder() throws Exception {
        // Seed: ORD-001 is held by AGT-001. AGT-002 and AGT-004 are both AVAILABLE at load 0,
        // so the agent-id tie-break must pick AGT-002.
        mockMvc.perform(post("/api/v1/orders/ORD-001/suggest"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.orderId").value("ORD-001"))
                .andExpect(jsonPath("$.recommendedAgentId").value("AGT-002"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.triggerReason").value("INITIAL"))
                .andExpect(jsonPath("$.strategyUsed").value("rule-based"))
                .andExpect(jsonPath("$.confidence").value(1.00))
                .andExpect(jsonPath("$.reasoning").isNotEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        assertThat(orderRepository.findById("ORD-001").orElseThrow())
                .satisfies(order -> {
                    assertThat(order.getStatus()).isEqualTo(OrderStatus.REASSIGNMENT_PENDING);
                    assertThat(order.getAssignedAgentId())
                            .as("the order does not move until the suggestion is accepted")
                            .isEqualTo("AGT-001");
                });
    }

    @Test
    void persistsAPendingSuggestionCarryingTheRoutingRationale() throws Exception {
        mockMvc.perform(post("/api/v1/orders/ORD-003/suggest"))
                .andExpect(status().isCreated());

        assertThat(suggestionRepository.findAll())
                .filteredOn(s -> s.getOrderId().equals("ORD-003"))
                .singleElement()
                .satisfies(suggestion -> {
                    assertThat(suggestion.getStatus()).isEqualTo(SuggestionStatus.PENDING);
                    assertThat(suggestion.getTriggerReason()).isEqualTo(TriggerReason.INITIAL);
                    assertThat(suggestion.getStrategyUsed()).isEqualTo("rule-based");
                    assertThat(suggestion.getRecommendedAgentId()).isEqualTo("AGT-002");
                    assertThat(suggestion.getConfidence())
                            .isEqualByComparingTo(new BigDecimal("1.00"));
                    assertThat(suggestion.getReasoning()).contains("Ranked #1");
                    assertThat(suggestion.getCreatedAt()).isNotNull();
                });
    }

    @Test
    void returnsNotFoundForUnknownOrder() throws Exception {
        mockMvc.perform(post("/api/v1/orders/ORD-999/suggest"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void rejectsARepeatedSuggestAndLeavesExactlyOneSuggestion() throws Exception {
        mockMvc.perform(post("/api/v1/orders/ORD-001/suggest"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/orders/ORD-001/suggest"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        assertThat(suggestionRepository.findAll())
                .filteredOn(s -> s.getOrderId().equals("ORD-001"))
                .as("a duplicate request must not create a second pending suggestion")
                .hasSize(1);
        assertThat(orderRepository.findById("ORD-001").orElseThrow().getStatus())
                .isEqualTo(OrderStatus.REASSIGNMENT_PENDING);
    }

    @Test
    void suggestedOrderCanThenBeReassignedThroughTheDecideEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/orders/ORD-001/suggest"))
                .andExpect(status().isCreated());
        Long suggestionId = suggestionRepository.findAll().stream()
                .filter(s -> s.getOrderId().equals("ORD-001"))
                .findFirst().orElseThrow().getId();

        mockMvc.perform(patch("/api/v1/suggestions/" + suggestionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isOk());

        assertThat(orderRepository.findById("ORD-001").orElseThrow())
                .satisfies(order -> {
                    assertThat(order.getStatus()).isEqualTo(OrderStatus.REASSIGNED);
                    assertThat(order.getAssignedAgentId()).isEqualTo("AGT-002");
                });
    }
}
