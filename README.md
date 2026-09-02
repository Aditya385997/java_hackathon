# ZipRun — delivery reassignment engine

Java 17 + Spring Boot 3.3 REST service. When a delivery agent goes offline, their orders
need to move to someone else; this service models the agents, orders, and the suggestions
that reassign them.

## Run

```bash
./mvnw spring-boot:run
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- H2 console: http://localhost:8080/h2-console  (JDBC URL `jdbc:h2:mem:appdb`, user `sa`)
- Health: http://localhost:8080/actuator/health

## Test

```bash
./mvnw test        # unit tests
./mvnw -B verify   # unit + integration (*IT) tests — run this before pushing
```

## Postgres profile

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres
```

Flyway owns the schema there (`src/main/resources/db/migration`), Hibernate is `validate` only.
The default H2 profile uses `create-drop` + `data.sql` for speed.

## Layout

```
com.aditya.app
  common/     ApiError, GlobalExceptionHandler, NotFoundException, BusinessRuleException
  config/     OpenApiConfig
  dispatch/   the ZipRun reassignment engine
    domain/   Agent, Order, ReassignmentSuggestion + their state machines
    dto/ repo/ service/ web/
```


Conventions and AI working agreement live in `CLAUDE.md`.

Data is stored in H2 in-memory and resets on restart. Seed data reloads from `data.sql`
on every boot. For persistence across restarts, run the `postgres` profile.

## API

| Method | Path | Notes |
| --- | --- | --- |
| POST | `/api/v1/orders` | Creates an ASSIGNED order, increments the agent's load |
| GET | `/api/v1/orders?status=` | Optional status filter |
| PATCH | `/api/v1/agents/{id}/status` | Availability only — reassignment is T-4 |
| PATCH | `/api/v1/suggestions/{id}` | ACCEPTED applies the move; REJECTED leaves the order pending |

Seeded with 5 agents (`AGT-001`..`AGT-005`) and 8 orders (`ORD-001`..`ORD-008`).
Sample requests are in `api.http`.

Conventions and AI working agreement live in `CLAUDE.md`; tradeoffs in `NOTES.md`.

