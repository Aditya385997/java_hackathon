package com.aditya.app.dispatch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateOrderRequest(
        @NotBlank(message = "id must not be blank")
        @Size(max = 32, message = "id must be at most 32 characters")
        String id,

        @NotBlank(message = "description must not be blank")
        @Size(max = 255, message = "description must be at most 255 characters")
        String description,

        @NotBlank(message = "assignedAgentId must not be blank")
        @Size(max = 32, message = "assignedAgentId must be at most 32 characters")
        String assignedAgentId
) {}
