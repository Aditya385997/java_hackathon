# Architecture Walkthrough — ZipRun Dispatch

Revision sheet. Everything here is **implemented and passing** (T-1, T-2, T-3) unless marked
**FUTURE**. Root package `com.aditya.app`, feature slice `dispatch`.

---

## 1. System flow

```
  Ops/Client                 Spring MVC              Service              Domain + H2
      |                          |                      |                      |
      |  POST /orders            |                      |                      |
      +------------------------->| OrderController      |                      |
      |                          +--------------------->| OrderService.create  |
      |                          |                      +--> agent.incrementLoad()
      |                          |                      +--> save Order(ASSIGNED)
      |                          |                      |
      |  POST /orders/{id}/suggest                      |
      +------------------------->| OrderController      |
      |                          +--------------------->| SuggestionService.suggest
      |                          |                      +--> RoutingStrategySelector.active()
      |                          |                      +--> strategy.recommend(ctx)
      |                          |                      +--> order -> REASSIGNMENT_PENDING
      |                          |                      +--> save ReassignmentSuggestion(PENDING)
      |                          |                      |
      |  PATCH /suggestions/{id} |                      |
      +------------------------->| SuggestionController |
      |                          +--------------------->| SuggestionService.decide
      |                          |                      +--> ACCEPTED: move order + both loads
      |                          |                      +--> REJECTED: suggestion only
      v                          v                      v                      v
```

Nothing moves an order automatically. A human decides. **FUTURE (T-4):** an
`AGENT_OFFLINE` event will raise suggestions without a human asking — the decision stays human.

---

## 2. Domain model

```
Agent                          Order                        ReassignmentSuggestion
-----                          -----                        ----------------------
id            String (PK)      id             String (PK)   id              Long (identity)
name          String           description    String        orderId         String  <- plain id
activeOrderCount int           assignedAgentId String        recommendedAgentId String <- plain id
status        AgentStatus      status         OrderStatus    confidence      BigDecimal(3,2)
zone*         String           createdAt      Instant        reasoning       String (TEXT)
maxCapacity*  Integer          zone*          String         status          SuggestionStatus
                               weightClass*   String         triggerReason   TriggerReason
                               slaDeadline*   Instant        strategyUsed    String
                                                             createdAt       Instant
* Sprint-2 placeholders: nullable columns, nothing reads them.
```

`ReassignmentSuggestion` holds **flat ids, not `@ManyToOne`** — it is an advisory record that
stays meaningful after the order moves on.

### State transitions (all enforced on the entity, not the service)

```
OrderStatus                                  SuggestionStatus
-----------                                  ----------------
ASSIGNED ---> REASSIGNMENT_PENDING           PENDING ---> ACCEPTED  (terminal)
   |                  |                          |
   |                  v                          +---> REJECTED  (terminal)
   |             REASSIGNED
   |                  |
   +---> DELIVERED <--+
```

Deliberate: **no `REASSIGNMENT_PENDING -> ASSIGNED` edge.** Rejecting a suggestion leaves the
order pending so another can be raised.

`AgentStatus` = `AVAILABLE | BUSY | OFFLINE`, **no transition restrictions**
(`Agent.changeStatus` accepts any target).

Guard-then-apply pair on both entities: `requireCanTransitionTo(...)` / `transitionTo(...)`
and `requireDecidable(...)` / `decide(...)` — so a caller can reject work before mutating.

---

## 3. API flows (all 6 implemented endpoints)

```
POST /api/v1/orders                        @Valid CreateOrderRequest
  -> OrderController.create
  -> OrderService.create
  -> existsById guard -> agentRepository.findById -> agent.incrementLoad() -> save Order
  => 201 + Location, OrderResponse (status ASSIGNED)

GET /api/v1/orders?status=
  -> OrderController.list
  -> OrderService.findAll(status)
  -> orderRepository.findAll() | findByStatus(status)
  => 200, List<OrderResponse>   (bad enum -> 400 with field violation)

POST /api/v1/orders/{id}/suggest           no body
  -> OrderController.suggest
  -> SuggestionService.suggest
  -> order.requireCanTransitionTo(REASSIGNMENT_PENDING) -> strategy.recommend(...)
     -> order.transitionTo(REASSIGNMENT_PENDING) -> save suggestion
  => 201 + Location, SuggestionResponse (PENDING)

PATCH /api/v1/agents/{id}/status           @Valid UpdateAgentStatusRequest
  -> AgentController.updateStatus
  -> AgentService.updateStatus
  -> agent.changeStatus(target) -> repository.save
  => 200, AgentResponse        (records availability only; OFFLINE reaction is FUTURE T-4)

PATCH /api/v1/suggestions/{id}             @Valid UpdateSuggestionRequest
  -> SuggestionController.decide
  -> SuggestionService.decide
  -> suggestion.requireDecidable -> [ACCEPTED: accept()] -> suggestion.decide -> save
  => 200, SuggestionResponse

GET  /api/v1/routing/strategy
PATCH /api/v1/routing/strategy             @Valid UpdateRoutingStrategyRequest
  -> RoutingController.current / switchStrategy
  -> RoutingStrategySelector.activeKey() / activate(key)
  => 200, RoutingStrategyResponse(active, available)
     unknown key -> 409 BusinessRuleException, previous strategy stays active
```

Errors are uniform via `GlobalExceptionHandler`: `NotFoundException` -> 404,
`BusinessRuleException` -> 409, validation -> 400 with `violations[]`, anything else -> 500.

---

## 4. T-1 reassignment flow

```
ACCEPT:  PATCH /suggestions/{id} {"status":"ACCEPTED"}
  suggestionRepository.findById              (404 if missing)
  suggestion.requireDecidable(ACCEPTED)      (409 if already decided)
  accept(suggestion):
      order.reassignTo(recommendedAgentId)   <- transitions FIRST (409 unless PENDING),
      orderRepository.save                      then assigns the agent
      previousAgent.decrementLoad()   -1     (skipped when the order had no agent)
      recommendedAgent.incrementLoad() +1
  suggestion.decide(ACCEPTED)
  => Order REASSIGNED, suggestion ACCEPTED — one transaction

REJECT:  PATCH /suggestions/{id} {"status":"REJECTED"}
  suggestion.requireDecidable(REJECTED)
  suggestion.decide(REJECTED)
  => suggestion REJECTED. Order untouched — stays REASSIGNMENT_PENDING,
     still held by the original agent. No load moves.
```

`accept()` runs **before** `suggestion.decide(...)`, so a failure anywhere in the move leaves
the suggestion undecided rather than marked ACCEPTED against a move that never happened.

---

## 5. T-2 routing flow

```
Order entity ──OrderSnapshot.from()──┐
                                     ├──> RoutingContext(order, agents, triggerReason, recovery)
Agent entities ─AgentSnapshot.from()─┘                                              |
                                                                                    | null for INITIAL
   RoutingStrategySelector.active()  ──resolved per call──> RoutingStrategy
                                                                 |
                                            List<RoutingRecommendation> (best first)
                                                                 |
                    empty? -> BusinessRuleException, nothing mutated
                                                                 |
                    recommendations.get(0) -> new ReassignmentSuggestion(...)
```

`RoutingRecommendation(recommendedAgentId, confidence, reasoning, strategyUsed)` carries
everything needed to persist — the orchestration layer never asks the strategy a follow-up.

Snapshots are **records built from the entities**, so a strategy running inside the caller's
transaction has nothing it could mutate or accidentally flush.

---

## 6. Rule-based logic (`RuleBasedRoutingStrategy`, key `"rule-based"`)

```
context.agents()                      all 5 seeded agents
  -> filter status == AVAILABLE       drops BUSY and OFFLINE
  -> filter id != order.assignedAgentId()   never recommend the current holder
  -> sort by activeOrderCount ASC     lightest load wins
       then by id ASC                 deterministic tie-break
  -> map every survivor to RoutingRecommendation(confidence 1.00, generated reasoning, "rule-based")
```

Confidence is a **fixed `1.00`** — it means "certain about the ordering this rule produced",
not "this agent is objectively best". A deterministic rule has no probability to report.

Eligibility filtering lives in the **strategy**, not the service, so a future strategy can
weigh an agent this one discards.

---

## 7. Runtime strategy switching

```
                    RoutingStrategy  (interface: key(), recommend(ctx))
                            |
        +-------------------+-------------------+
        |                   |                   |
  RuleBasedRoutingStrategy  AiRoutingStrategy   ZoneAffinityStrategy  <- FUTURE
     key "rule-based"        key "ai"              key "zone-affinity"
```

**Registry** (`RoutingStrategyRegistry`) — Spring injects `List<RoutingStrategy>`; it keys every
bean by `key()` in a sorted map. Duplicate key = startup failure. It answers *"what exists?"*
and is immutable after construction.

**Selector** (`RoutingStrategySelector`) — holds *which one is active right now* in an
`AtomicReference<String>`, seeded from `routing.strategy` and swapped by `activate(key)`.
It answers *"what is active?"*.

`active()` re-resolves **per call**, so `SuggestionService` — which holds the selector as a
constructor-injected field for the life of the app — sees a switch on the very next request.
No restart, no bean rebuild. The active key is in-memory and **resets to `rule-based` on
restart**.

---

## 8. T-3 AI flow (`AiRoutingStrategy`, key `"ai"`)

```
RoutingContext
   -> AiPrompts.forContext(ctx)         switch on ctx.triggerReason()
        INITIAL        -> AiPrompts.initial(ctx)
        AGENT_OFFLINE  -> AiPrompts.agentOfflineRecovery(ctx)
   -> LlmPrompt(systemInstruction, userPrompt)
   -> LLMGateway.complete(prompt)                     <- interface, transport only
        GeminiLLMGateway:
          POST {llm.base-url}/models/{llm.model}:generateContent
          headers  x-goog-api-key, Content-Type: json, Accept: json
          body     systemInstruction + contents + generationConfig
                   (responseMimeType application/json, temperature 0,
                    maxOutputTokens = llm.max-output-tokens)
          response read as byte[] -> objectMapper.readTree(...)   <- ignores Content-Type
          requireCompleteAnswer(candidate)   finishReason must be STOP
          extract candidates[0].content.parts[0].text
   -> objectMapper.readValue(raw, AiRecommendation.class)
   -> firstViolation(answer, ctx)                     <- validation gates, section 9
   -> RoutingRecommendation(agentId, confidence.setScale(2), reasoning, "ai")
```

Two gateway details that came from **real provider failures**, not theory:

- Body is read as `byte[]` and parsed by us. Gemini labelled a JSON body
  `application/octet-stream`; binding to `JsonNode` made Spring pick a converter by
  Content-Type and fail. Bytes (not `String`) because `StringHttpMessageConverter` defaults to
  ISO-8859-1 for an unlabelled charset and would corrupt non-ASCII in verbatim reasoning.
- `finishReason` is checked. `maxOutputTokens` on Gemini 3.x is a **combined thinking + answer
  budget**; too small a value truncates the JSON mid-string. Now rejected as truncation with an
  actionable message instead of surfacing as "not parseable JSON".

Reasoning is stored **verbatim**. Confidence is range-checked **raw**, then `setScale(2)` purely
to fit the `NUMERIC(3,2)` column — normalisation, never a change of decision.

---

## 9. AI validation gates (`AiRoutingStrategy.firstViolation`)

The model's answer is untrusted input. First failing gate wins and returns a reason string.

```
raw text
  1. parses as JSON into AiRecommendation?            no -> "not parseable JSON"
  2. recommendedAgentId non-blank?                    no -> "recommendedAgentId is missing"
  3. present in context.agents()?                     no -> "was never offered as a candidate"   (hallucination)
  4. that agent's status == AVAILABLE?                no -> "is BUSY/OFFLINE, not AVAILABLE"
  5. != order.assignedAgentId()?                      no -> "already holds order X"
  6. confidence non-null and 0 <= c <= 1?             no -> "confidence X is outside [0,1]"
  7. reasoning non-blank?                             no -> "reasoning is blank"
  -> all pass: accept the model's answer as-is
```

Order matters: the status gate (4) runs before the current-agent gate (5).

**The whole answer is rejected on the first violation — never repaired.** A clamped confidence
or a substituted agent is no longer the model's answer, and `strategyUsed: "ai"` would then be
a lie.

---

## 10. Fallback

```
LlmGatewayException (no API key / timeout / quota / HTTP 4xx-5xx /
                     non-JSON body / finishReason != STOP)
JsonProcessingException (malformed or truncated JSON)
any validation gate from section 9 (hallucinated agent, BUSY/OFFLINE,
                     current agent, invalid confidence, blank reasoning)
        |
        v
  log.warn("AI routing fell back to rule-based for order {}: {}", orderId, reason)
        |
        v
  ruleBased.recommend(context)        <- returned UNMODIFIED
        |
        v
  RoutingRecommendation(..., strategyUsed = "rule-based")
```

`strategyUsed` names the real producer with no relabelling code: the AI path stamps `"ai"`, the
fallback path returns rule-based's own objects which already say `"rule-based"`.
`SuggestionService` logs `top.strategyUsed()` for the same reason.

If rule-based *also* has no candidate, an empty list comes back and `SuggestionService` throws
`BusinessRuleException` **before any write**.

### FALLBACK vs ROLLBACK — different layers, do not confuse them

| | FALLBACK | ROLLBACK |
|---|---|---|
| What | alternative routing **algorithm** | database transaction reverted |
| Trigger | AI unusable (failure or failed validation) | any exception in a `@Transactional` method |
| Owned by | `AiRoutingStrategy` | Spring + JPA |
| Result | a valid rule-based recommendation; request **succeeds** (201) | no partial writes; request **fails** (409/404/500) |
| Visible as | `strategyUsed: "rule-based"` + WARN log | error response, DB unchanged |

An AI failure is normally a fallback, **not** a rollback — the request still returns 201. A
rollback only happens if something later in the transaction throws.

---

## 11. Transactions / rollback

Every service is `@Transactional(readOnly = true)` at class level; write methods override it.

| Method | Annotation | State protected |
|---|---|---|
| `OrderService.create` | `@Transactional` | order insert **and** the assigned agent's `activeOrderCount` +1 — both or neither |
| `OrderService.findAll` | class-level readOnly | — |
| `AgentService.updateStatus` | `@Transactional` | single agent row |
| `SuggestionService.suggest` | `@Transactional` | order -> `REASSIGNMENT_PENDING` **and** the new suggestion row |
| `SuggestionService.decide` | `@Transactional` | the whole accept: order -> `REASSIGNED`, previous agent -1, recommended agent +1, suggestion -> ACCEPTED |

`decide` is the one that matters most: an accept moves **three** rows plus the suggestion. If
`decrementLoad()` throws (counter already at zero), everything rolls back — the order does not
move with only one counter updated.

Ordering is defensive on top of that: `suggest` calls `requireCanTransitionTo` before doing any
routing work, and `reassignTo` transitions before assigning, so an invalid request fails with
nothing mutated and the rollback has less to undo.

`spring.jpa.open-in-view: false` — no lazy loading outside the service transaction.

---

## 12. Two prompts (`AiPrompts`)

| | INITIAL | AGENT_OFFLINE |
|---|---|---|
| Role given | "dispatch routing assistant" | "dispatch **recovery planner**" |
| Framing | "Nothing has failed", routine optimisation | "incident recovery, **not** routine optimisation" |
| Extra facts | — | `failedAgentId`, `strandedOrderCount`, "this is one of them" |
| Decision rule | best fit, balance the roster | prefer an agent with **headroom to absorb more** — the other stranded orders still need placing |
| Audience | operator approving a suggestion | dispatcher working a live incident |

Shared: the order, every agent with status + load, and the JSON response contract.

**Why genuinely different:** recovery is not the same decision relabelled. Optimising for *this*
order would spend all remaining slack on it and strand the rest; the recovery prompt optimises
for absorbing the remaining N-1 orders too. That changes the answer, not just the wording.

**FUTURE (T-4):** `SuggestionService` currently always passes `TriggerReason.INITIAL`, and
`RecoveryContext(failedAgentId, strandedOrderCount)` is designed and tested but **nothing
populates it yet**. The recovery prompt exists and is unit-tested; T-4 wires the trigger.

---

## 13. Two callers, one interface

```
NOW      HTTP POST /orders/{id}/suggest
             -> SuggestionService.suggest
                 -> RoutingStrategySelector.active()
                     -> RoutingStrategy.recommend(ctx)      [synchronous]

FUTURE   PATCH /agents/{id}/status {"status":"OFFLINE"}
   (T-4)     -> responds 200 immediately
             -> publishes an event
             -> @Async handler on a background thread
                 -> routing orchestration
                     -> RoutingStrategy.recommend(ctx)      [same bean, same method]
```

`RoutingStrategy` takes a `RoutingContext` and returns recommendations. It touches no
repository, holds no mutable state, and never learns who called it. `AiRoutingStrategy` is
stateless and thread-safe, so a background thread can use it concurrently with an HTTP thread —
**no change needed for T-4**.

**Critical path:** the state-change endpoints (`POST /orders`, `PATCH /agents/{id}/status`,
`PATCH /suggestions/{id}`) must never wait on a model. `POST /suggest` stays synchronous on
purpose — a human explicitly asked for a recommendation and the model's answer *is* the
response body. Its worst case is bounded by `llm.timeout` plus the mandatory fallback.

---

## 14. Future extension — adding `ZoneAffinityStrategy`

```java
@Component
public class ZoneAffinityStrategy implements RoutingStrategy {
    public String key() { return "zone-affinity"; }
    public List<RoutingRecommendation> recommend(RoutingContext context) { ... }
}
```

That is the whole change. Then:

```
Spring injects it into RoutingStrategyRegistry's List<RoutingStrategy>   (automatic)
  -> GET  /api/v1/routing/strategy   now lists it in "available"
  -> PATCH /api/v1/routing/strategy  {"strategy":"zone-affinity"}  activates it
  -> SuggestionService.suggest       uses it on the next call, unchanged
```

No controller, service, registry or selector edit. It would read `Agent.zone` / `Order.zone`,
the Sprint-2 placeholder columns that already exist.

---

## 15. Interview cheat sheet

**Why the Strategy pattern?** The brief requires swapping routing algorithms at runtime. A
`switch` on a strategy name would put every algorithm in one class and force a caller edit per
algorithm. The interface made T-3 additive: `AiRoutingStrategy` landed without touching the HTTP
layer. *(Knowingly breaks CLAUDE.md's "no interface with one implementation" — the seam was the
deliverable; called out in NOTES.md.)*

**Why immutable snapshots?** `RoutingContext` carries `OrderSnapshot`/`AgentSnapshot` records,
not JPA entities. A strategy runs inside the caller's transaction, so handing it entities means
a stray setter could be flushed to the DB. Records make "routing recommends, it never mutates" a
structural guarantee — and give the AI strategy a clean serialisable payload for free.

**Why a registry?** It answers "what strategies exist?". Spring injects every `RoutingStrategy`
bean, so registering one is just adding a bean. Duplicate keys fail at startup, not at 3am.

**Why a selector?** It answers "which one is active *right now*?" — mutable process state that
has to change without a restart. Splitting it from the registry keeps an immutable lookup
separate from a mutable pointer.

**How does runtime switching work?** `AtomicReference<String>` holding the active key.
`PATCH /routing/strategy` validates against the registry, then `getAndSet`. `active()` resolves
per call, so a long-lived holder like `SuggestionService` sees the change on the next request.
Trade-off: in-memory, so it resets to `routing.strategy` on restart.

**Why validate LLM output?** It is untrusted input arriving over the network. A model can name
an agent that does not exist, is OFFLINE, or already holds the order; it can return confidence
`1.5` or empty reasoning. Acting on that would corrupt dispatch state. Six gates, reject the
whole answer on the first failure — never repair it, or `strategyUsed: "ai"` becomes a lie.

**Why rule-based fallback?** The AI is an enhancement, not a dependency. No API key, a quota
error, a timeout, or a hallucination all still produce a correct, explainable recommendation.
The user gets a 201 either way; only `strategyUsed` and the WARN log differ.

**Fallback vs rollback?** Fallback = a different routing *algorithm* after the AI is unusable;
the request still succeeds. Rollback = the *database transaction* reverting after an exception;
the request fails. An AI failure is a fallback, not a rollback. See the table in section 10.

**Why two prompts?** Recovery after a failure is a different decision from routine
optimisation — it must reserve capacity for the other stranded orders, so it optimises for a
different thing, not just with different wording. One template with a swapped label would not
change the answer.

**Why no automatic reassignment?** Every path ends at a `PENDING` suggestion a human accepts or
rejects. Dispatch is a real-world action; an LLM recommending it is useful, an LLM executing it
unsupervised is not. It also keeps the AI advisory, so a bad recommendation costs a rejected
suggestion, not a misrouted parcel.

**How will T-4 reuse this?** `PATCH /agents/{id}/status` returns 200 first, then an `@Async`
handler re-plans on a background thread through the same `RoutingStrategy` bean. The strategy is
stateless and never learns who called it. `RoutingContext.recovery` and the AGENT_OFFLINE prompt
already exist — T-4 populates them; the interface does not change.

**How would `ZoneAffinityStrategy` be added?** One `@Component implements RoutingStrategy` with
`key()` returning `"zone-affinity"`. The registry discovers it, the endpoint switches to it,
`SuggestionService` is untouched. See section 14.

---

## Quick reference

| Thing | Value |
|---|---|
| Strategy keys | `rule-based` (default), `ai` |
| Registered order | sorted — `["ai", "rule-based"]` |
| Config | `routing.strategy`, `llm.base-url`, `llm.model`, `llm.api-key` (`${GEMINI_API_KEY:}`), `llm.timeout`, `llm.max-output-tokens` |
| No key configured | `ai` still registers and switches; every call falls back, reason logged |
| Seed | 5 agents `AGT-001..005`, 8 orders `ORD-001..008`, all ASSIGNED |
| DB | H2 in-memory, `create-drop`, `data.sql` on boot; `postgres` profile uses Flyway + `validate` |
| Tests | 108 — 80 unit + 28 integration (`./mvnw -B verify`) |
