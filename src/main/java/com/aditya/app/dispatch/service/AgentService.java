package com.aditya.app.dispatch.service;

import com.aditya.app.common.NotFoundException;
import com.aditya.app.dispatch.domain.Agent;
import com.aditya.app.dispatch.domain.AgentWentOfflineEvent;
import com.aditya.app.dispatch.domain.AgentStatus;
import com.aditya.app.dispatch.dto.AgentResponse;
import com.aditya.app.dispatch.repo.AgentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final AgentRepository repository;
    private final ApplicationEventPublisher events;

    public AgentService(AgentRepository repository, ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    /**
     * Updates availability and returns. Going OFFLINE publishes an event; re-planning that
     * agent's orders happens on a background thread after this transaction commits, so the
     * caller is never made to wait on routing (or on a language model).
     */
    @Transactional
    public AgentResponse updateStatus(String id, AgentStatus target) {
        Agent agent = getOrThrow(id);
        agent.changeStatus(target);
        AgentResponse response = AgentResponse.from(repository.save(agent));
        log.info("Agent {} status set to {}", id, target);
        if (target == AgentStatus.OFFLINE) {
            events.publishEvent(new AgentWentOfflineEvent(id));
        }
        return response;
    }

    private Agent getOrThrow(String id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Agent", id));
    }
}
