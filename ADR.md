# Architecture Decision Records — ZipRun Dispatch

One line of context, the alternative rejected, and why. Fuller tradeoff notes live in `NOTES.md`.

---

### ADR-1 — Routing behind a `RoutingStrategy` interface
**Context:** the brief requires swapping routing algorithms at runtime.
**Decision:** `RoutingStrategy` (`key()`, `recommend(RoutingContext)`) with strategies as Spring beans.
**Rejected:** one routing service with a `switch` on a strategy name.
**Why:** every algorithm would land in one class and each new one would force a caller edit. T-3's `AiRoutingStrategy` and T-4's async caller both landed without touching the HTTP layer.
**Cost:** a knowing exception to CLAUDE.md's "no interface with a single implementation" — the seam *was* the T-2 deliverable.

### ADR-2 — Immutable snapshots in `RoutingContext`
**Decision:** strategies receive `OrderSnapshot` / `AgentSnapshot` records, never JPA entities.
**Rejected:** passing `Order` and `Agent` straight through.
**Why:** a strategy runs inside the caller's transaction; handing it entities means a stray mutation gets flushed. Records make "routing recommends, it never mutates" structural, and gave T-3 a serialisable LLM payload for free.
**Cost:** one small mapping step per call.

### ADR-3 — Registry and Selector as separate objects
**Decision:** `RoutingStrategyRegistry` (immutable, keyed by `key()`, built from injected `List<RoutingStrategy>`) plus `RoutingStrategySelector` (`AtomicReference` holding the active key).
**Rejected:** one class owning both; `@Value("${routing.strategy}")` alone.
**Why:** "what exists" is immutable and validated at startup (duplicate keys fail the context); "what is active" is mutable process state that must change without a restart, which `@Value` cannot do. `active()` resolves per call, so long-lived holders see a switch immediately.
**Cost:** the active key is in-memory and resets to `routing.strategy` on restart. A persisted config store can replace it without touching the seam.

### ADR-4 — Gemini behind a minimal `LLMGateway`
**Decision:** a one-method interface returning raw model text; `GeminiLLMGateway` calls the REST API with Spring's `RestClient`.
**Rejected:** Spring AI or a provider SDK; a gateway returning a pre-validated recommendation.
**Why:** no new dependency, and one boundary where model output stops being trusted. Parsing and validation live in `AiRoutingStrategy`, so swapping providers touches one class and no validation test.
**Learned in production:** the gateway reads the body as `byte[]` and parses it itself (Gemini labelled JSON as `application/octet-stream`), and checks `finishReason` (on Gemini 3.x `maxOutputTokens` is a combined thinking+answer budget, so a small value truncates the JSON mid-string).

### ADR-5 — Deterministic rule-based fallback, never repair
**Decision:** six validation gates; the whole answer is rejected on the first failure and `RuleBasedRoutingStrategy`'s recommendations are returned unmodified, with the reason logged.
**Rejected:** clamping confidence, substituting the next-best agent, retrying the model.
**Why:** a repaired answer is no longer the model's answer and `strategyUsed: "ai"` would be a lie. The AI is an enhancement, not a dependency — no key, a quota error or a hallucination still yields a correct, explainable recommendation.
**Consequence:** `strategyUsed` names the real producer with no relabelling code.

### ADR-6 — Asynchronous in-process event for offline re-planning
**Decision:** `AgentService` publishes `AgentWentOfflineEvent`; `AgentOfflineReplanner` consumes it with `@Async @TransactionalEventListener` (AFTER_COMMIT).
**Rejected:** re-planning inline in the PATCH request.
**Why:** the official brief requires AI calls off the critical path. `PATCH /agents/{id}/status` must record a failure immediately and must not fail because a model was slow. AFTER_COMMIT guarantees the agent really is OFFLINE before the roster is read.
**Cost:** an in-flight re-plan is lost if the process dies — see ADR-9.

### ADR-7 — Human approval before any reassignment
**Decision:** every path — manual and automatic — ends at a PENDING `ReassignmentSuggestion`. Only `PATCH /api/v1/suggestions/{id}` moves an order or an agent's load.
**Rejected:** auto-assigning the recommended agent on high confidence.
**Why:** dispatch is a real-world action. An LLM recommending is useful; an LLM executing unsupervised is not. The blast radius of a wrong answer is a rejected suggestion, not a misrouted parcel. It also keeps confidence honest — nothing is gated on a self-reported number.

### ADR-8 — Duplicate suppression by query, not by state machine
**Decision:** before routing an order the replanner checks `existsByOrderIdAndStatusAndTriggerReason(orderId, PENDING, AGENT_OFFLINE)`.
**Rejected:** an in-memory processed-agents set; a dedicated dedup table.
**Why:** the database already holds the fact. Repeated OFFLINE events converge on the same set of pending suggestions, and it survives a restart, which an in-memory set would not.
**Cost:** a check per order; a narrow race under concurrent duplicate events, acceptable at this scope.

### ADR-9 — No message broker
**Decision:** Spring's in-process `ApplicationEventPublisher`.
**Rejected:** Kafka / RabbitMQ / an outbox table.
**Why:** a broker adds infrastructure, a dependency and operational surface for a single-process hackathon service. The event never leaves the JVM, so the publisher is enough to get the work off the request thread.
**Known limit:** delivery is at-most-once and in-memory. A crash between commit and handling loses that re-plan; there is no retry and no dead-letter path.
**Scaling path:** the seam is `AgentWentOfflineEvent` + `AgentOfflineReplanner.replan`. Writing the event to an outbox table in the same transaction and having a relay publish to a durable broker would make it at-least-once without changing `SuggestionService` or any strategy — the handler body stays as it is.

### ADR-10 — H2 in-memory by default, Postgres + Flyway as the production path
**Decision:** default profile is H2 `create-drop` re-seeded from `data.sql`; the `postgres` profile uses Flyway with Hibernate on `validate`.
**Why:** fast startup, clean test isolation and repeatable demos in dev; versioned migrations in prod.
**Cost:** dev data does not survive a restart — intentional. The Flyway DDL is not exercised by `./mvnw verify`.
