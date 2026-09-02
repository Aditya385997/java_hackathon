package com.aditya.app.dispatch;

import com.aditya.app.common.BusinessRuleException;
import com.aditya.app.common.NotFoundException;
import com.aditya.app.dispatch.domain.Agent;
import com.aditya.app.dispatch.domain.AgentStatus;
import com.aditya.app.dispatch.domain.Order;
import com.aditya.app.dispatch.domain.OrderStatus;
import com.aditya.app.dispatch.domain.ReassignmentSuggestion;
import com.aditya.app.dispatch.domain.SuggestionStatus;
import com.aditya.app.dispatch.domain.TriggerReason;
import com.aditya.app.dispatch.dto.SuggestionResponse;
import com.aditya.app.dispatch.repo.AgentRepository;
import com.aditya.app.dispatch.repo.OrderRepository;
import com.aditya.app.dispatch.repo.ReassignmentSuggestionRepository;
import com.aditya.app.dispatch.service.SuggestionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuggestionServiceTest {

    @Mock
    private ReassignmentSuggestionRepository suggestionRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private AgentRepository agentRepository;

    @InjectMocks
    private SuggestionService service;

    private final Agent previousAgent = new Agent("AGT-001", "Priya Sharma", AgentStatus.OFFLINE);
    private final Agent recommendedAgent = new Agent("AGT-004", "Kiran Nair", AgentStatus.AVAILABLE);

    private ReassignmentSuggestion pendingSuggestion() {
        return new ReassignmentSuggestion("ORD-001", "AGT-004", new BigDecimal("0.87"),
                "AGT-001 went offline; AGT-004 is idle", TriggerReason.AGENT_OFFLINE, "TEST");
    }

    private Order pendingOrder() {
        Order order = new Order("ORD-001", "Electronics", "AGT-001");
        order.transitionTo(OrderStatus.REASSIGNMENT_PENDING);
        return order;
    }

    private void stubLookups(ReassignmentSuggestion suggestion, Order order) {
        when(suggestionRepository.findById(1L)).thenReturn(Optional.of(suggestion));
        lenient().when(orderRepository.findById("ORD-001")).thenReturn(Optional.of(order));
        lenient().when(agentRepository.findById("AGT-001")).thenReturn(Optional.of(previousAgent));
        lenient().when(agentRepository.findById("AGT-004")).thenReturn(Optional.of(recommendedAgent));
        lenient().when(suggestionRepository.save(any(ReassignmentSuggestion.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void acceptTransfersLoadFromPreviousAgentToRecommended() {
        previousAgent.incrementLoad();
        previousAgent.incrementLoad();
        Order order = pendingOrder();
        stubLookups(pendingSuggestion(), order);

        SuggestionResponse response = service.decide(1L, SuggestionStatus.ACCEPTED);

        assertThat(response.status()).isEqualTo(SuggestionStatus.ACCEPTED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REASSIGNED);
        assertThat(order.getAssignedAgentId()).isEqualTo("AGT-004");
        assertThat(previousAgent.getActiveOrderCount()).isEqualTo(1);
        assertThat(recommendedAgent.getActiveOrderCount()).isEqualTo(1);
    }

    @Test
    void rejectsAcceptWhenOrderIsNotReassignmentPending() {
        previousAgent.incrementLoad();
        Order stillAssigned = new Order("ORD-001", "Electronics", "AGT-001");
        stubLookups(pendingSuggestion(), stillAssigned);

        assertThatThrownBy(() -> service.decide(1L, SuggestionStatus.ACCEPTED))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(stillAssigned.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        assertThat(previousAgent.getActiveOrderCount()).isEqualTo(1);
        assertThat(recommendedAgent.getActiveOrderCount()).isZero();
    }

    @Test
    void rejectLeavesOrderReassignmentPendingAndCountersUnchanged() {
        previousAgent.incrementLoad();
        Order order = pendingOrder();
        stubLookups(pendingSuggestion(), order);

        SuggestionResponse response = service.decide(1L, SuggestionStatus.REJECTED);

        assertThat(response.status()).isEqualTo(SuggestionStatus.REJECTED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REASSIGNMENT_PENDING);
        assertThat(order.getAssignedAgentId()).isEqualTo("AGT-001");
        assertThat(previousAgent.getActiveOrderCount()).isEqualTo(1);
        assertThat(recommendedAgent.getActiveOrderCount()).isZero();
    }

    @Test
    void rejectsDecisionOnTerminalSuggestion() {
        previousAgent.incrementLoad();
        ReassignmentSuggestion decided = pendingSuggestion();
        decided.decide(SuggestionStatus.REJECTED);
        Order order = pendingOrder();
        stubLookups(decided, order);

        assertThatThrownBy(() -> service.decide(1L, SuggestionStatus.ACCEPTED))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("REJECTED");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REASSIGNMENT_PENDING);
        assertThat(previousAgent.getActiveOrderCount()).isEqualTo(1);
        assertThat(recommendedAgent.getActiveOrderCount()).isZero();
    }

    @Test
    void throwsNotFoundForUnknownSuggestion() {
        when(suggestionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.decide(99L, SuggestionStatus.ACCEPTED))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void throwsNotFoundWhenRecommendedAgentIsMissing() {
        previousAgent.incrementLoad();
        Order order = pendingOrder();
        when(suggestionRepository.findById(1L)).thenReturn(Optional.of(pendingSuggestion()));
        when(orderRepository.findById("ORD-001")).thenReturn(Optional.of(order));
        when(agentRepository.findById("AGT-001")).thenReturn(Optional.of(previousAgent));
        when(agentRepository.findById("AGT-004")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.decide(1L, SuggestionStatus.ACCEPTED))
                .isInstanceOf(NotFoundException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REASSIGNMENT_PENDING);
    }
}
