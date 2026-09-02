package com.aditya.app.dispatch.service;

import com.aditya.app.common.BusinessRuleException;
import com.aditya.app.common.NotFoundException;
import com.aditya.app.dispatch.domain.Agent;
import com.aditya.app.dispatch.domain.Order;
import com.aditya.app.dispatch.domain.OrderStatus;
import com.aditya.app.dispatch.domain.ReassignmentSuggestion;
import com.aditya.app.dispatch.domain.SuggestionStatus;
import com.aditya.app.dispatch.domain.TriggerReason;
import com.aditya.app.dispatch.dto.SuggestionResponse;
import com.aditya.app.dispatch.repo.AgentRepository;
import com.aditya.app.dispatch.repo.OrderRepository;
import com.aditya.app.dispatch.repo.ReassignmentSuggestionRepository;
import com.aditya.app.dispatch.routing.AgentSnapshot;
import com.aditya.app.dispatch.routing.OrderSnapshot;
import com.aditya.app.dispatch.routing.RoutingContext;
import com.aditya.app.dispatch.routing.RoutingRecommendation;
import com.aditya.app.dispatch.routing.RoutingStrategy;
import com.aditya.app.dispatch.routing.RoutingStrategySelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class SuggestionService {

    private static final Logger log = LoggerFactory.getLogger(SuggestionService.class);

    private final ReassignmentSuggestionRepository suggestionRepository;
    private final OrderRepository orderRepository;
    private final AgentRepository agentRepository;
    private final RoutingStrategySelector strategySelector;

    public SuggestionService(ReassignmentSuggestionRepository suggestionRepository,
                             OrderRepository orderRepository,
                             AgentRepository agentRepository,
                             RoutingStrategySelector strategySelector) {
        this.suggestionRepository = suggestionRepository;
        this.orderRepository = orderRepository;
        this.agentRepository = agentRepository;
        this.strategySelector = strategySelector;
    }

    /**
     * Produces an INITIAL recommendation for an order and parks it pending an operator's
     * decision. Routing runs through whichever strategy is active, so this method never
     * needs to know which one that is.
     *
     * <p>A second call for the same order fails: the order is REASSIGNMENT_PENDING by then
     * and the state machine refuses to move it there again.
     */
    @Transactional
    public SuggestionResponse suggest(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order", orderId));
        // Fail before doing routing work the transaction would only roll back.
        order.requireCanTransitionTo(OrderStatus.REASSIGNMENT_PENDING);

        RoutingStrategy strategy = strategySelector.active();
        RoutingContext context = new RoutingContext(
                OrderSnapshot.from(order),
                agentRepository.findAll().stream().map(AgentSnapshot::from).toList(),
                TriggerReason.INITIAL);

        List<RoutingRecommendation> recommendations = strategy.recommend(context);
        if (recommendations.isEmpty()) {
            throw new BusinessRuleException("No eligible agent available to take order " + orderId);
        }
        RoutingRecommendation top = recommendations.get(0);

        order.transitionTo(OrderStatus.REASSIGNMENT_PENDING);
        orderRepository.save(order);

        ReassignmentSuggestion saved = suggestionRepository.save(new ReassignmentSuggestion(
                order.getId(), top.recommendedAgentId(), top.confidence(), top.reasoning(),
                TriggerReason.INITIAL, top.strategyUsed()));
        // Logs the producer, not the selection: a strategy that fell back internally reports
        // the strategy that actually answered, matching the persisted strategyUsed.
        log.info("Strategy '{}' recommended agent {} for order {}; suggestion {} is pending",
                top.strategyUsed(), top.recommendedAgentId(), orderId, saved.getId());
        return SuggestionResponse.from(saved);
    }

    /**
     * Applies an operator's decision. Accepting moves the order and both agents' load
     * counters in a single transaction; any failure along the way rolls the whole thing back.
     */
    @Transactional
    public SuggestionResponse decide(Long id, SuggestionStatus target) {
        ReassignmentSuggestion suggestion = suggestionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Suggestion", id));
        suggestion.requireDecidable(target);

        if (target == SuggestionStatus.ACCEPTED) {
            accept(suggestion);
        }

        suggestion.decide(target);
        log.info("Suggestion {} for order {} decided as {}",
                id, suggestion.getOrderId(), target);
        return SuggestionResponse.from(suggestionRepository.save(suggestion));
    }

    private void accept(ReassignmentSuggestion suggestion) {
        Order order = orderRepository.findById(suggestion.getOrderId())
                .orElseThrow(() -> new NotFoundException("Order", suggestion.getOrderId()));
        Agent previousAgent = resolvePreviousAgent(order);
        Agent recommendedAgent = agentRepository.findById(suggestion.getRecommendedAgentId())
                .orElseThrow(() -> new NotFoundException("Agent", suggestion.getRecommendedAgentId()));

        // Throws unless the order is REASSIGNMENT_PENDING, before any counter moves.
        order.reassignTo(recommendedAgent.getId());
        orderRepository.save(order);

        if (previousAgent != null) {
            previousAgent.decrementLoad();
            agentRepository.save(previousAgent);
        }
        recommendedAgent.incrementLoad();
        agentRepository.save(recommendedAgent);
    }

    /** Null only when the order carries no agent, in which case there is no load to release. */
    private Agent resolvePreviousAgent(Order order) {
        String previousAgentId = order.getAssignedAgentId();
        if (previousAgentId == null) {
            return null;
        }
        return agentRepository.findById(previousAgentId)
                .orElseThrow(() -> new NotFoundException("Agent", previousAgentId));
    }
}
