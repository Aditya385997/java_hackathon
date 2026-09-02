package com.aditya.app.dispatch.service;

import com.aditya.app.common.BusinessRuleException;
import com.aditya.app.common.NotFoundException;
import com.aditya.app.dispatch.domain.Agent;
import com.aditya.app.dispatch.domain.Order;
import com.aditya.app.dispatch.domain.OrderStatus;
import com.aditya.app.dispatch.dto.CreateOrderRequest;
import com.aditya.app.dispatch.dto.OrderResponse;
import com.aditya.app.dispatch.repo.AgentRepository;
import com.aditya.app.dispatch.repo.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final AgentRepository agentRepository;

    public OrderService(OrderRepository orderRepository, AgentRepository agentRepository) {
        this.orderRepository = orderRepository;
        this.agentRepository = agentRepository;
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        if (orderRepository.existsById(request.id())) {
            throw new BusinessRuleException("Order already exists: " + request.id());
        }
        Agent agent = agentRepository.findById(request.assignedAgentId())
                .orElseThrow(() -> new NotFoundException("Agent", request.assignedAgentId()));

        agent.incrementLoad();
        agentRepository.save(agent);

        Order saved = orderRepository.save(
                new Order(request.id(), request.description(), agent.getId()));
        log.info("Created order {} assigned to agent {}", saved.getId(), agent.getId());
        return OrderResponse.from(saved);
    }

    public List<OrderResponse> findAll(OrderStatus status) {
        List<Order> orders = (status == null)
                ? orderRepository.findAll()
                : orderRepository.findByStatus(status);
        return orders.stream().map(OrderResponse::from).toList();
    }
}
