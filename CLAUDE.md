# Project Context

Java 21 + Spring Boot 3.3 REST service. Maven. Built under hackathon time pressure
but must read as production code. Root package: `com.aditya.app`.

## Commands

- Run: `./mvnw spring-boot:run`   (H2, seeded, http://localhost:8080/swagger-ui.html)
- Test: `./mvnw test`
- Full verify before any push: `./mvnw -B verify`

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
