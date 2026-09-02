package com.aditya.app.dispatch.routing.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * The shape the model is asked to answer in. Every field is untrusted — a value arriving
 * here has been parsed, nothing more. {@code AiRoutingStrategy} decides whether it is usable.
 *
 * <p>Unknown properties are ignored rather than rejected: a model that volunteers an extra
 * field has still answered the question, and failing on it would trade a good recommendation
 * for a needless fallback.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiRecommendation(String recommendedAgentId, BigDecimal confidence, String reasoning) {}
