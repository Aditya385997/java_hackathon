package com.aditya.app.dispatch.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateRoutingStrategyRequest(
        @NotBlank(message = "strategy is required") String strategy
) {}
