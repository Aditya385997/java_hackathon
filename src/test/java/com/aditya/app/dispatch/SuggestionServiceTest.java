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
import com.aditya.app.dispatch.routing.RoutingContext;
import com.aditya.app.dispatch.routing.RoutingRecommendation;
import com.aditya.app.dispatch.routing.RoutingStrategy;
import com.aditya.app.dispatch.routing.RoutingStrategySelector;
import com.aditya.app.dispatch.service.SuggestionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuggestionServiceTest {

    @Mock
    private ReassignmentSuggestionRepository suggestionRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private RoutingStrategySelector strategySelector;

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

    // ---------- suggest(): T-2 routing orchestration ----------

    /** Records the context it was handed so tests can assert what routing actually saw. */
    private static final class RecordingStrategy implements RoutingStrategy {
        private final List<RoutingRecommendation> result;
        private RoutingContext received;

        private RecordingStrategy(List<RoutingRecommendation> result) {
            this.result = result;
        }

        @Override
        public String key() {
            return "recording";
        }

        @Override
        public List<RoutingRecommendation> recommend(RoutingContext context) {
            this.received = context;
            return result;
        }
    }

    private static RecordingStrategy strategyReturning(String... agentIds) {
        return new RecordingStrategy(java.util.Arrays.stream(agentIds)
                .map(id -> new RoutingRecommendation(id, new BigDecimal("1.00"),
                        "because " + id, "recording"))
                .toList());
    }

    private Order assignedOrder() {
        return new Order("ORD-001", "Electronics", "AGT-001");
    }

    @Test
    void suggestPersistsTheTopRecommendationAndParksTheOrder() {
        Order order = assignedOrder();
        RecordingStrategy strategy = strategyReturning("AGT-004", "AGT-002");
        when(strategySelector.active()).thenReturn(strategy);
        when(orderRepository.findById("ORD-001")).thenReturn(Optional.of(order));
        when(agentRepository.findAll()).thenReturn(List.of(previousAgent, recommendedAgent));
        when(suggestionRepository.save(any(ReassignmentSuggestion.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SuggestionResponse response = service.suggest("ORD-001");

        assertThat(response.recommendedAgentId())
                .as("the head of the ordered list wins").isEqualTo("AGT-004");
        assertThat(response.status()).isEqualTo(SuggestionStatus.PENDING);
        assertThat(response.triggerReason()).isEqualTo(TriggerReason.INITIAL);
        assertThat(response.strategyUsed()).isEqualTo("recording");
        assertThat(response.confidence()).isEqualByComparingTo(new BigDecimal("1.00"));
        assertThat(response.reasoning()).isEqualTo("because AGT-004");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REASSIGNMENT_PENDING);
        assertThat(order.getAssignedAgentId())
                .as("suggesting must not move the order yet").isEqualTo("AGT-001");
    }

    @Test
    void suggestRoutesThroughTheActiveStrategyWithInitialTrigger() {
        RecordingStrategy strategy = strategyReturning("AGT-004");
        when(strategySelector.active()).thenReturn(strategy);
        when(orderRepository.findById("ORD-001")).thenReturn(Optional.of(assignedOrder()));
        when(agentRepository.findAll()).thenReturn(List.of(previousAgent, recommendedAgent));
        when(suggestionRepository.save(any(ReassignmentSuggestion.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.suggest("ORD-001");

        verify(strategySelector).active();
        assertThat(strategy.received.triggerReason()).isEqualTo(TriggerReason.INITIAL);
        assertThat(strategy.received.order().id()).isEqualTo("ORD-001");
        assertThat(strategy.received.agents())
                .as("the whole roster goes to the strategy, which owns eligibility")
                .extracting(a -> a.id()).containsExactly("AGT-001", "AGT-004");
    }

    @Test
    void suggestFailsWithoutMutatingWhenNoAgentIsEligible() {
        Order order = assignedOrder();
        when(strategySelector.active()).thenReturn(strategyReturning());
        when(orderRepository.findById("ORD-001")).thenReturn(Optional.of(order));
        when(agentRepository.findAll()).thenReturn(List.of(previousAgent));

        assertThatThrownBy(() -> service.suggest("ORD-001"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ORD-001");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        verify(suggestionRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void suggestRejectsAnOrderThatIsAlreadyAwaitingReassignment() {
        Order order = assignedOrder();
        order.transitionTo(OrderStatus.REASSIGNMENT_PENDING);
        when(orderRepository.findById("ORD-001")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.suggest("ORD-001"))
                .isInstanceOf(BusinessRuleException.class);

        verify(suggestionRepository, never()).save(any());
        verify(strategySelector, never())
                .active();   // rejected before any routing work is done
    }

    @Test
    void suggestRejectsADeliveredOrder() {
        Order order = assignedOrder();
        order.transitionTo(OrderStatus.DELIVERED);
        when(orderRepository.findById("ORD-001")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.suggest("ORD-001"))
                .isInstanceOf(BusinessRuleException.class);
        verify(suggestionRepository, never()).save(any());
    }

    @Test
    void suggestThrowsNotFoundForUnknownOrder() {
        when(orderRepository.findById("ORD-999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.suggest("ORD-999"))
                .isInstanceOf(NotFoundException.class);
    }
}
