package com.aditya.app.dispatch;

import com.aditya.app.common.BusinessRuleException;
import com.aditya.app.common.NotFoundException;
import com.aditya.app.dispatch.domain.Agent;
import com.aditya.app.dispatch.domain.AgentStatus;
import com.aditya.app.dispatch.domain.Order;
import com.aditya.app.dispatch.domain.OrderStatus;
import com.aditya.app.dispatch.dto.CreateOrderRequest;
import com.aditya.app.dispatch.dto.OrderResponse;
import com.aditya.app.dispatch.repo.AgentRepository;
import com.aditya.app.dispatch.repo.OrderRepository;
import com.aditya.app.dispatch.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private AgentRepository agentRepository;

    @InjectMocks
    private OrderService service;

    @Test
    void createsOrderAsAssignedAndIncrementsAgentLoad() {
        Agent agent = new Agent("AGT-002", "Rahul Verma", AgentStatus.AVAILABLE);
        when(orderRepository.existsById("ORD-009")).thenReturn(false);
        when(agentRepository.findById("AGT-002")).thenReturn(Optional.of(agent));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = service.create(
                new CreateOrderRequest("ORD-009", "Test parcel", "AGT-002"));

        assertThat(response.status()).isEqualTo(OrderStatus.ASSIGNED);
        assertThat(response.assignedAgentId()).isEqualTo("AGT-002");
        assertThat(agent.getActiveOrderCount()).isEqualTo(1);
    }

    @Test
    void rejectsDuplicateOrderId() {
        when(orderRepository.existsById("ORD-001")).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                new CreateOrderRequest("ORD-001", "Duplicate", "AGT-002")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ORD-001");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void rejectsOrderForUnknownAgent() {
        when(orderRepository.existsById("ORD-009")).thenReturn(false);
        when(agentRepository.findById("AGT-999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(
                new CreateOrderRequest("ORD-009", "Test parcel", "AGT-999")))
                .isInstanceOf(NotFoundException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void returnsEveryOrderWhenNoStatusFilterIsGiven() {
        when(orderRepository.findAll()).thenReturn(List.of(
                new Order("ORD-001", "One", "AGT-001"),
                new Order("ORD-002", "Two", "AGT-003")));

        assertThat(service.findAll(null)).extracting(OrderResponse::id)
                .containsExactly("ORD-001", "ORD-002");
    }

    @Test
    void filtersByStatusWhenGiven() {
        when(orderRepository.findByStatus(OrderStatus.ASSIGNED))
                .thenReturn(List.of(new Order("ORD-001", "One", "AGT-001")));

        assertThat(service.findAll(OrderStatus.ASSIGNED)).extracting(OrderResponse::id)
                .containsExactly("ORD-001");
    }
}
