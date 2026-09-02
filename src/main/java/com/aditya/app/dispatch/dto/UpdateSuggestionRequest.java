package com.aditya.app.dispatch.dto;

import com.aditya.app.dispatch.domain.SuggestionStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateSuggestionRequest(
        @NotNull(message = "status is required") SuggestionStatus status
) {}
