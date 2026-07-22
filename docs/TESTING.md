# Testing

## Commands

```bash
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}
export PATH="$JAVA_HOME/bin:$PATH"

# Colima users:
export DOCKER_HOST=unix://$HOME/.colima/default/docker.sock
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock

./mvnw test
./mvnw clean verify
```

`./mvnw clean verify` runs Surefire, Failsafe (including concurrency + OpenAPI drift checks),
JaCoCo coverage gates, Spotless formatting checks, and SpotBugs.

### Formatting (Spotless / Google Java Format)

```bash
./mvnw spotless:check   # fail if sources are not formatted
./mvnw spotless:apply   # rewrite Java sources (and selected YAML/XML) to match
```

Formatting is also enforced during `verify` — do not commit without `spotless:check` passing.

### OpenAPI specification

The committed file [`docs/openapi.yaml`](openapi.yaml) must match the live springdoc document.
`OpenApiSpecIT` compares them on every verify. To regenerate after changing controllers/DTOs:

```bash
./mvnw -Dopenapi.update=true -Dit.test=OpenApiSpecIT verify
```

## Layers

| Layer | Tools | Focus |
|-------|-------|-------|
| Unit | JUnit 5, Mockito | Auth, parsers, HMAC, overload rules, stats math |
| Controller | MockMvc | Validation, authz, status codes |
| Integration | Testcontainers PostgreSQL | Flyway, JWT flows, webhooks, imports, E2E, OpenAPI export |
| Quality gates | Spotless, SpotBugs, JaCoCo | Format, static analysis, coverage |

## Colima note

Testcontainers needs a reachable Docker API. With Colima, set both `DOCKER_HOST` and `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock` so Ryuk can manage containers inside the VM.

## Coverage

JaCoCo runs on `verify`. Service packages target ≥60% line coverage (see `pom.xml`). Prefer meaningful business-logic coverage over chasing percentages on boilerplate.
