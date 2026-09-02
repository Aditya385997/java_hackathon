package com.aditya.app.dispatch;

import com.aditya.app.dispatch.domain.OrderStatus;
import com.aditya.app.dispatch.domain.ReassignmentSuggestion;
import com.aditya.app.dispatch.domain.SuggestionStatus;
import com.aditya.app.dispatch.domain.TriggerReason;
import com.aditya.app.dispatch.repo.AgentRepository;
import com.aditya.app.dispatch.repo.OrderRepository;
import com.aditya.app.dispatch.repo.ReassignmentSuggestionRepository;
import com.aditya.app.dispatch.service.AgentOfflineReplanner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives {@link AgentOfflineReplanner#replan} directly. The production path is
 * {@code @Async @TransactionalEventListener}, which by design never fires inside a test that
 * rolls its transaction back — so the event wiring is asserted in AgentServiceTest and the
 * loop's behaviour is asserted here.
 *
 * <p>Seed: AGT-001 is BUSY holding ORD-001, ORD-002 and ORD-008, all ASSIGNED.
 */
@SpringBootTest
@Transactional
class AgentOfflineReplannerIT {

    @Autowired
    private AgentOfflineReplanner replanner;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private ReassignmentSuggestionRepository suggestionRepository;

    private List<ReassignmentSuggestion> offlineSuggestions() {
        return suggestionRepository.findAll().stream()
                .filter(s -> s.getTriggerReason() == TriggerReason.AGENT_OFFLINE)
                .toList();
    }

    @Test
    void raisesAPendingAgentOfflineSuggestionForEveryStrandedOrder() {
        replanner.replan("AGT-001");

        assertThat(offlineSuggestions())
                .hasSize(3)
                .allSatisfy(s -> {
                    assertThat(s.getStatus()).isEqualTo(SuggestionStatus.PENDING);
                    assertThat(s.getTriggerReason()).isEqualTo(TriggerReason.AGENT_OFFLINE);
                    assertThat(s.getStrategyUsed()).isNotBlank();
                    assertThat(s.getReasoning()).isNotBlank();
                })
                .extracting(ReassignmentSuggestion::getOrderId)
                .containsExactlyInAnyOrder("ORD-001", "ORD-002", "ORD-008");
    }

    @Test
    void neverReassignsTheOrderItself() {
        replanner.replan("AGT-001");

        assertThat(orderRepository.findById("ORD-001").orElseThrow()).satisfies(order -> {
            assertThat(order.getStatus())
                    .as("the order is parked for a human, not moved")
                    .isEqualTo(OrderStatus.REASSIGNMENT_PENDING);
            assertThat(order.getAssignedAgentId())
                    .as("the offline agent still holds it until Ops accepts")
                    .isEqualTo("AGT-001");
        });
        assertThat(agentRepository.findById("AGT-002").orElseThrow().getActiveOrderCount())
                .as("no load moves without a human decision")
                .isZero();
    }

    @Test
    void skipsOrdersThatAlreadyHaveAPendingOfflineSuggestion() {
        replanner.replan("AGT-001");
        assertThat(offlineSuggestions()).hasSize(3);

        // A second OFFLINE event for the same agent must not double up.
        replanner.replan("AGT-001");

        assertThat(offlineSuggestions())
                .as("duplicate AGENT_OFFLINE suggestions must not accumulate")
                .hasSize(3);
    }

    @Test
    void doesNothingForAnAgentHoldingNoAssignedOrders() {
        replanner.replan("AGT-002");

        assertThat(offlineSuggestions()).isEmpty();
    }
}
