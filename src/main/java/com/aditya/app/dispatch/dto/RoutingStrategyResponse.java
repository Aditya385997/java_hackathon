package com.aditya.app.dispatch.dto;

import java.util.Set;

public record RoutingStrategyResponse(String active, Set<String> available) {}
