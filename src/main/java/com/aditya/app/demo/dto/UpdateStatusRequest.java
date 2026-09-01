package com.aditya.app.demo.dto;

import com.aditya.app.demo.domain.TaskStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(
        @NotNull(message = "status is required") TaskStatus status
) {}
