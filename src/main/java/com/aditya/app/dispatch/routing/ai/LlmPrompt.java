package com.aditya.app.dispatch.routing.ai;

/**
 * One prompt, split the way every chat-completion provider splits it: standing instructions
 * that set the model's role, and the per-request facts it reasons over.
 */
public record LlmPrompt(String systemInstruction, String userPrompt) {}
