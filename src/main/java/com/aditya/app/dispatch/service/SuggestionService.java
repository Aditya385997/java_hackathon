package com.aditya.app.dispatch.service;

import com.aditya.app.common.NotFoundException;
import com.aditya.app.dispatch.domain.Agent;
import com.aditya.app.dispatch.domain.Order;
import com.aditya.app.dispatch.domain.ReassignmentSuggestion;
import com.aditya.app.dispatch.domain.SuggestionStatus;
import com.aditya.app.dispatch.dto.SuggestionResponse;
import com.aditya.app.dispatch.repo.AgentRepository;
import com.aditya.app.dispatch.repo.OrderRepository;
import com.aditya.app.dispatch.repo.ReassignmentSuggestionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SuggestionService {

    private static final Logger log = LoggerFactory.getLogger(SuggestionService.class);

    private final ReassignmentSuggestionRepository suggestionRepository;
    private final OrderRepository orderRepository;
    private final AgentRepository agentRepository;

    public SuggestionService(ReassignmentSuggestionRepository suggestionRepository,
                             OrderRepository orderRepository,
                             AgentRepository agentRepository) {
        this.suggestionRepository = suggestionRepository;
        this.orderRepository = orderRepository;
        this.agentRepository = agentRepository;
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
