# Project Context

Java 17 + Spring Boot 3.3 REST service. Maven. Built under hackathon time pressure
but must read as production code. Root package: `com.aditya.app`.

## Commands

- Run: `./mvnw spring-boot:run`   (H2, seeded, http://localhost:8080/swagger-ui.html)
- Test: `./mvnw test`
- Full verify before any push: `./mvnw -B verify`
- Debug: VS Code "Debug app (H2)" config (F5). Hot code replace is on — method-body
  edits apply live, structural changes need a restart.
- Debug via Maven: `./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"`
  then attach with the "Attach on 5005" config.
- H2 console: http://localhost:8080/h2-console — JDBC URL `jdbc:h2:mem:appdb`, user `sa`, blank password.
- API docs JSON (import into Postman): http://localhost:8080/v3/api-docs

## Non-negotiable conventions

- Constructor injection only. No `@Autowired` on fields. No field/setter injection.
- Controllers never accept or return JPA entities. DTOs (Java records) at the boundary,
  mapped in the service layer.
- Controllers are thin: validate, delegate, return. No business logic, no repository calls.
- All business logic lives in `@Service` classes. Repositories are called only from services.
- Every request DTO is validated with Jakarta Bean Validation (`@Valid` on the controller param).
- Never throw raw `RuntimeException`. Use `NotFoundException` / `BusinessRuleException` from
  `com.aditya.app.common`. They are translated by `GlobalExceptionHandler` into `ApiError`.
- Never catch an exception and swallow it. Never `e.printStackTrace()`.
- Use `Optional` returns from repositories; resolve them in the service, not the controller.
- Money uses `BigDecimal`. Time uses `Instant` (UTC) or `LocalDate`. Never `Date`, never `float`.
- Domain state uses enums, never magic strings.
- Use SLF4J (`private static final Logger log = LoggerFactory.getLogger(X.class)`), not
  `System.out`. Log at service boundaries, not inside loops.

## Package layout

Feature-first. Each feature gets `domain/ dto/ repo/ service/ web/` under
`com.aditya.app.<feature>`. Cross-cutting code goes in `common/` or `config/`.
`com.aditya.app.demo` is the reference slice — copy its shape, then delete it once
the real features exist.

## Frontend

React + Vite in `frontend/`. Plain JS, no TypeScript, no UI or state library.
- Dev: `cd frontend && npm run dev` (port 5173, proxies /api to 8080)
- All HTTP calls go in `src/api.js`. Components never call fetch directly.
- Errors surface the backend's ApiError `message` field.
- Keep components small and dumb. useState/useEffect only.

## Testing

- Every service method with a branch gets a unit test (JUnit 5 + Mockito, no Spring context).
- Every controller gets at least one `@SpringBootTest` + `MockMvc` integration test covering
  the happy path and one 400/404 case.
- Test names describe behaviour: `rejectsApprovalWhenRequesterIsApprover`.
- Do not write a test that only asserts a mock was called.

## Git

- Small, working commits. One logical change per commit.
- Conventional commits: `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`.
- Never commit a state where `./mvnw test` fails.
- Never commit secrets, `target/`, or IDE files.

## How I want you to work

- Before writing code for a new feature, state the plan in 5 lines or fewer: entities,
  endpoints, key business rules. Wait for my go-ahead.
- Build one vertical slice at a time (entity -> repo -> service -> controller -> tests),
  and stop after each so I can review the diff. Do not chain multiple features unprompted.
- Prefer the smallest change that works. No speculative abstraction, no interface with a
  single implementation, no new dependency without asking.
- If a requirement is ambiguous, ask one question rather than guessing.
- When you make a non-obvious tradeoff, add one line to `NOTES.md` explaining why.

## Design lens
Every design choice must answer: scalable, reliable, maintainable.
When they conflict under time pressure, maintainability wins and the tradeoff goes in NOTES.md.

## Data persistence

Default profile uses H2 in-memory (`jdbc:h2:mem:appdb`) with `ddl-auto: create-drop`.
Data does not survive restarts — this is intentional for test isolation and clean demos.
`data.sql` re-seeds on every boot. Do not switch to file-based H2 unless requirements
demand persistence across restarts.
The `postgres` profile is the production path: Flyway owns the schema, Hibernate is `validate` only.

## Repo hygiene

- Never commit `target/`, `frontend/node_modules/`, `frontend/dist/`, or IDE files.
- Do not create new top-level directories or config files without asking.
- Do not add dependencies to `pom.xml` or `package.json` without asking.
- Do not modify `CLAUDE.md`, `README.md`, or `NOTES.md` unless I ask — I maintain those.