package com.aditya.app.dispatch.routing.ai;

/**
 * The one place the application talks to a language model. Transport only: it returns the
 * model's raw text and does not parse, interpret or vouch for it.
 *
 * <p>That line is deliberate. Everything downstream treats the result as untrusted input,
 * and {@code AiRoutingStrategy} owns the single validation boundary — so swapping providers
 * touches this abstraction and nothing that decides whether an answer is usable.
 */
public interface LLMGateway {

    /**
     * @return the model's raw response text, never null
     * @throws LlmGatewayException on a missing credential, timeout, quota or provider error,
     *         a non-2xx status, or a response envelope with no text in it
     */
    String complete(LlmPrompt prompt);
}
