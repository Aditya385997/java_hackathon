package com.aditya.app.dispatch.dto;

import com.aditya.app.dispatch.domain.AgentStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateAgentStatusRequest(
        @NotNull(message = "status is required") AgentStatus status
) {}
