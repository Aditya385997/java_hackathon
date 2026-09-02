package com.aditya.app.dispatch.routing.ai;

/**
 * A language model could not be reached or did not answer usefully.
 *
 * <p>Deliberately not a {@code NotFoundException} or {@code BusinessRuleException}: those are
 * translated into HTTP responses, and this one must never reach the web layer.
 * {@code AiRoutingStrategy} always catches it, logs the reason and falls back to the
 * rule-based strategy, so a provider outage is invisible to the caller.
 */
public class LlmGatewayException extends RuntimeException {

    public LlmGatewayException(String message) {
        super(message);
    }

    public LlmGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
