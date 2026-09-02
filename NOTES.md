# Implementation Notes

## Approach

T-1 builds the ZipRun dispatch domain: `Agent`, `Order`, `ReassignmentSuggestion`, their
state machines, and the four endpoints that create orders, list them, change agent
availability, and apply an operator's decision on a suggestion. Nothing in this slice
*decides* where an order should go — routing, strategies, and the agentic loop are T-2/T-4.
The reference `demo` slice was deleted once `dispatch` replaced it.

## Architecture decisions

| Decision | Alternative considered | Why this one |
| --- | --- | --- |
| Transition rules live on the entity (`Order.transitionTo`, `ReassignmentSuggestion.decide`) | Status checks in the service layer | The illegal-transition rule can't be bypassed by a future caller, and it unit-tests without a Spring context. Follows the demo `Task` shape. |
| `ReassignmentSuggestion` holds `orderId` / `recommendedAgentId` as `String` | `@ManyToOne` associations | A suggestion is an advisory record, not an owned relationship — it stays meaningful after the order moves on. Flat ids keep the response DTO and the future LLM payload trivial. Cost: no DB-level referential integrity on those two columns. |
| Three services (`OrderService`, `AgentService`, `SuggestionService`) | One `DispatchService` | Only `SuggestionService` needs all three repositories; keeping order/agent CRUD out of it keeps each class small. |
| `Order.reassignTo` transitions *before* assigning the agent | Assign then validate | An out-of-state reassign fails with nothing mutated, so the transaction rollback has less to undo and the entity is never briefly inconsistent. |
| `Agent.decrementLoad()` throws rather than clamping at zero | `Math.max(0, count - 1)` | A decrement below zero means the counters have drifted from reality. Silently clamping hides that; T-2's load balancing would then read a wrong number. |
| Integration tests are `@Transactional` | Fresh context or explicit cleanup per test | They mutate the shared seeded H2 rows; without rollback they become order-dependent. Cheaper than rebuilding the context. |
| Routing behind a `RoutingStrategy` interface + `RoutingStrategyRegistry` + `RoutingStrategySelector` | A single routing service with a `switch` on strategy name | The HTTP caller and T-4's async handler depend only on the contract and `SuggestionService.suggest`. `AiRoutingStrategy` (T-3) and a future `ZoneAffinityStrategy` register as Spring beans and become switchable with no caller change. |
| Active strategy held in an `AtomicReference`, switched via `PATCH /api/v1/routing/strategy` | `@Value("${routing.strategy}")` alone; a dynamic-config dependency | The brief requires switching without a restart, which `@Value` cannot do. **Tradeoff:** the active key is in-memory and resets to the configured default on restart. Sufficient for the hackathon; a persisted config store can replace it later without touching the seam. |
| `RoutingContext` carries immutable `OrderSnapshot`/`AgentSnapshot` records | Passing the JPA entities | Structurally enforces "routing recommends, it never mutates" — a strategy running inside the caller's transaction has nothing it could accidentally flush. Costs one small mapping step, and gives T-3 a clean serializable payload. |
| Eligibility filtering lives in the strategy, not the service | Service pre-filters to AVAILABLE, strategy only ranks | Lets a future zone- or capacity-aware strategy weigh an agent this one discards, without the caller changing. Deviates from the brief's literal step ordering; it is what makes the strategy's exclusion tests meaningful. |
| Rule-based confidence is a fixed `1.00` | Deriving it from list position (1.00, 0.90, 0.80...) | The value means "certain about the ordering this rule produced", not "100% likely the objectively best agent". A deterministic rule carries no probability, so position-derived numbers would be arbitrary pseudo-scoring. Genuine model-returned confidence arrives with T-3. |
| `RoutingStrategy` is an interface with one implementation | Concrete class now, extract the interface in T-3 | **A knowing exception to CLAUDE.md's "no interface with a single implementation".** The seam is the deliverable: T-3 adds the AI strategy and T-4 an async caller, and both must land without reworking the HTTP layer. Called out here so the conventions file and the code do not silently disagree. |

## Corrections to AI output

- **Integration tests were never running.** `./mvnw verify` reported green while silently
  skipping every `*IT` class — Surefire's default includes are `*Test`/`*Tests`, and no
  Failsafe execution was configured. The scaffold's original `TaskControllerIT` had never
  run either. Added `maven-failsafe-plugin` (version managed by
  `spring-boot-starter-parent`, no new dependency) so `verify` actually executes them:
  24 unit + 15 integration.
- **`Order` needs an explicit `@Table(name = "orders")`.** The derived name from the class
  would be `order`, a reserved word, and the schema fails to create without it.
- Seeded `activeOrderCount` does not match the seeded order rows: the fixture gives
  AGT-001 = 2 against 3 order rows, and AGT-003 = 1 against 2. The Addendum A fixture is
  official and is preserved exactly as issued. Flagged here because T-2's load balancing
  reads that counter and will otherwise start from a skewed picture.

## Known gaps / what I'd do with more time

- `POST /api/v1/orders/{id}/suggest` (T-2) now raises suggestions, so the accept/reject path
  is reachable end to end without hand-seeding rows.
- Only `rule-based` is registered. The switch endpoint is real but has nothing to switch to
  until T-3 registers `ai`.
- `PATCH /agents/{id}/status` records availability and stops there. Reacting to OFFLINE is T-4.
- Sprint-2 placeholders (`Agent.zone`, `Agent.maxCapacity`, `Order.zone`,
  `Order.weightClass`, `Order.slaDeadline`) are nullable columns with no behaviour attached.
- The `postgres` profile's `V1__init.sql` was rewritten for the new schema but is not
  exercised by `./mvnw verify` — the H2 profile uses `create-drop`, so Flyway's DDL and
  Hibernate's `validate` have not been cross-checked against a real Postgres.
- `frontend/` still calls the removed `/api/v1/tasks` and will 404 until T-5 replaces it.
  It builds fine; it was deliberately left untouched.

## AI-assisted development

- Tool: Claude Code, with conventions pinned in `CLAUDE.md`.
- Workflow: plan first -> one vertical slice at a time -> review diff -> commit.

- Corrections made to AI output: (list them — this is the part reviewers care about)

## Known gaps / what I'd do with more time

- H2 in-memory for development, Postgres + Flyway for production. Dev gets fast startup and
  clean test isolation; prod gets versioned migrations with Hibernate restricted to `validate`.
  Tradeoff: dev data doesn't persist across restarts, which is acceptable and desirable here.

