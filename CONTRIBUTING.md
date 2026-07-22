# Contributing to GymSync API

Thanks for your interest in contributing.

## Development setup

1. Install Java 21 and Docker (or Colima).
2. Copy `.env.example` to `.env` and adjust values locally (never commit secrets).
3. Start PostgreSQL: `docker compose up -d`
4. Run the app: `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`
5. Open Swagger UI: http://localhost:8080/swagger-ui.html

## Before opening a pull request

- Run `./mvnw clean verify`
- Prefer small, focused pull requests
- Update `docs/` when behavior or decisions change
- Do not commit credentials, JWT secrets, or webhook secrets

## Code style

- Constructor injection
- DTOs at the API boundary (no JPA entities in responses)
- Business rules in services with `@Transactional`
- Domain exceptions via the `ApiException` hierarchy

## Security

Never log passwords, JWTs, webhook signatures, authorization headers, or LLM API keys.
