package com.aditya.app.dispatch.domain;

/**
 * Raised after an agent's status has been committed as OFFLINE. Carries the id only —
 * the handler runs on another thread and must re-read state rather than trust a snapshot
 * taken inside the publishing transaction.
 */
public record AgentWentOfflineEvent(String agentId) {}
