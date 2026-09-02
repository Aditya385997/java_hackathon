package com.aditya.app.dispatch.web;

import com.aditya.app.dispatch.dto.AgentResponse;
import com.aditya.app.dispatch.dto.UpdateAgentStatusRequest;
import com.aditya.app.dispatch.service.AgentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agents")
public class AgentController {

    private final AgentService service;

    public AgentController(AgentService service) {
        this.service = service;
    }

    @PatchMapping("/{id}/status")
    public AgentResponse updateStatus(@PathVariable String id,
                                      @Valid @RequestBody UpdateAgentStatusRequest request) {
        return service.updateStatus(id, request.status());
    }
}
