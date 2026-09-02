package com.aditya.app.dispatch.service;

import com.aditya.app.dispatch.domain.AgentWentOfflineEvent;
import com.aditya.app.dispatch.domain.Order;
import com.aditya.app.dispatch.domain.OrderStatus;
import com.aditya.app.dispatch.domain.SuggestionStatus;
import com.aditya.app.dispatch.domain.TriggerReason;
import com.aditya.app.dispatch.repo.OrderRepository;
import com.aditya.app.dispatch.repo.ReassignmentSuggestionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * The agentic loop: observe an agent failure, re-plan its stranded work, and check in with a
 * human before anything moves.
 *
 * <p>Runs on a background thread after the status change has committed, so
 * {@code PATCH /agents/{id}/status} answers immediately and never waits on routing or on a
 * language model. It owns no routing logic of its own — every order goes through
 * {@link SuggestionService#suggestForOfflineAgent}, the same orchestration the HTTP flow uses.
 */
@Service
public class AgentOfflineReplanner {

    private static final Logger log = LoggerFactory.getLogger(AgentOfflineReplanner.class);

    private final OrderRepository orderRepository;
    private final ReassignmentSuggestionRepository suggestionRepository;
    private final SuggestionService suggestionService;

    public AgentOfflineReplanner(OrderRepository orderRepository,
                                 ReassignmentSuggestionRepository suggestionRepository,
                                 SuggestionService suggestionService) {
        this.orderRepository = orderRepository;
        this.suggestionRepository = suggestionRepository;
        this.suggestionService = suggestionService;
    }

    /**
     * AFTER_COMMIT so the agent really is OFFLINE before anything reads the roster, and
     * {@code @Async} so the HTTP thread is already gone by the time routing starts.
     */
    @Async
    @TransactionalEventListener
    public void onAgentWentOffline(AgentWentOfflineEvent event) {
        replan(event.agentId());
    }

    /**
     * The loop itself, synchronous and callable on its own — an @Async AFTER_COMMIT listener
     * never fires inside a test that rolls its transaction back, so this is what tests drive.
     */
    public void replan(String failedAgentId) {
        List<Order> stranded = orderRepository
                .findByAssignedAgentIdAndStatus(failedAgentId, OrderStatus.ASSIGNED);
        if (stranded.isEmpty()) {
            log.info("Agent {} went OFFLINE holding no assigned orders; nothing to re-plan",
                    failedAgentId);
            return;
        }
        log.info("Agent {} went OFFLINE with {} stranded order(s); re-planning",
                failedAgentId, stranded.size());

        int raised = 0;
        for (Order order : stranded) {
            // One order that cannot be routed — no eligible agent, say — must not strand the rest.
            try {
                if (alreadyAwaitingDecision(order.getId())) {
                    log.info("Order {} already has a pending AGENT_OFFLINE suggestion; skipping",
                            order.getId());
                    continue;
                }
                suggestionService.suggestForOfflineAgent(
                        order.getId(), failedAgentId, stranded.size());
                raised++;
            } catch (RuntimeException e) {
                log.warn("Could not re-plan order {} after agent {} went offline: {}",
                        order.getId(), failedAgentId, e.getMessage());
            }
        }
        log.info("Re-planning for agent {} raised {} of {} suggestion(s), all pending approval",
                failedAgentId, raised, stranded.size());
    }

    private boolean alreadyAwaitingDecision(String orderId) {
        return suggestionRepository.existsByOrderIdAndStatusAndTriggerReason(
                orderId, SuggestionStatus.PENDING, TriggerReason.AGENT_OFFLINE);
    }
}
