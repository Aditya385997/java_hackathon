package com.aditya.app.dispatch.service;

import com.aditya.app.common.NotFoundException;
import com.aditya.app.dispatch.domain.Agent;
import com.aditya.app.dispatch.domain.AgentStatus;
import com.aditya.app.dispatch.dto.AgentResponse;
import com.aditya.app.dispatch.repo.AgentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final AgentRepository repository;

    public AgentService(AgentRepository repository) {
        this.repository = repository;
    }

    /**
     * Updates availability and nothing else. Reacting to OFFLINE by reassigning that
     * agent's orders is T-4.
     */
    @Transactional
    public AgentResponse updateStatus(String id, AgentStatus target) {
        Agent agent = getOrThrow(id);
        agent.changeStatus(target);
        log.info("Agent {} status set to {}", id, target);
        return AgentResponse.from(repository.save(agent));
    }

    private Agent getOrThrow(String id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Agent", id));
    }
}
