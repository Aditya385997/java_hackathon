# Hackathon Seed

Java 17 + Spring Boot 3.3 REST service scaffold. Replace this README with the real one
once the problem statement lands.

## Run

```bash
./mvnw spring-boot:run
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- H2 console: http://localhost:8080/h2-console  (JDBC URL `jdbc:h2:mem:appdb`, user `sa`)
- Health: http://localhost:8080/actuator/health

## Test

```bash
./mvnw test
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
  common/   ApiError, GlobalExceptionHandler, NotFoundException, BusinessRuleException
  config/   OpenApiConfig
  demo/     reference vertical slice — copy the shape, then delete
    domain/ dto/ repo/ service/ web/
```

Conventions and AI working agreement live in `CLAUDE.md`.

Data is stored in H2 in-memory and resets on restart. Seed data reloads from `data.sql`
on every boot. For persistence across restarts, run the `postgres` profile.
