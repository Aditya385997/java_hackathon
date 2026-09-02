package com.aditya.app.dispatch;

import com.aditya.app.common.BusinessRuleException;
import com.aditya.app.dispatch.domain.Order;
import com.aditya.app.dispatch.domain.OrderStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStateMachineTest {

    private static Order newOrder() {
        return new Order("ORD-100", "Test parcel", "AGT-001");
    }

    @Test
    void startsAssigned() {
        assertThat(newOrder().getStatus()).isEqualTo(OrderStatus.ASSIGNED);
    }

    @Test
    void walksTheFullReassignmentPath() {
        Order order = newOrder();

        order.transitionTo(OrderStatus.REASSIGNMENT_PENDING);
        order.transitionTo(OrderStatus.REASSIGNED);
        order.transitionTo(OrderStatus.DELIVERED);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void allowsDeliveryWithoutReassignment() {
        Order order = newOrder();

        order.transitionTo(OrderStatus.DELIVERED);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void rejectsSkippingReassignmentPendingToDelivered() {
        Order order = newOrder();
        order.transitionTo(OrderStatus.REASSIGNMENT_PENDING);

        assertThatThrownBy(() -> order.transitionTo(OrderStatus.DELIVERED))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("REASSIGNMENT_PENDING");
    }

    @Test
    void rejectsReturningToAssignedAfterReassignmentIsPending() {
        Order order = newOrder();
        order.transitionTo(OrderStatus.REASSIGNMENT_PENDING);

        assertThatThrownBy(() -> order.transitionTo(OrderStatus.ASSIGNED))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsAnyTransitionFromDelivered() {
        Order order = newOrder();
        order.transitionTo(OrderStatus.DELIVERED);

        assertThatThrownBy(() -> order.transitionTo(OrderStatus.REASSIGNMENT_PENDING))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void reassignMovesTheOrderToTheNewAgent() {
        Order order = newOrder();
        order.transitionTo(OrderStatus.REASSIGNMENT_PENDING);

        order.reassignTo("AGT-004");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REASSIGNED);
        assertThat(order.getAssignedAgentId()).isEqualTo("AGT-004");
    }

    @Test
    void rejectsReassignWhenOrderIsNotReassignmentPending() {
        Order order = newOrder();

        assertThatThrownBy(() -> order.reassignTo("AGT-004"))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(order.getAssignedAgentId())
                .as("the agent must not change when the transition is refused")
                .isEqualTo("AGT-001");
    }
}
