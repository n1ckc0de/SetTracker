# Architecture

## Overview

GymSync API is a **modular monolith** Spring Boot service. Modules share one PostgreSQL database and one deployable JAR. Boundaries are package-level, not network-level.

```mermaid
flowchart TB
  clients[Clients / Swagger / Webhooks]
  api[Spring MVC Controllers]
  ws[STOMP WebSocket]
  svc[Application Services]
  sec[JWT Security]
  jpa[Spring Data JPA]
  db[(PostgreSQL)]
  llm[WorkoutNotesParser]
  broker[Simple STOMP Broker]

  clients --> api
  clients --> ws
  api --> sec
  ws --> sec
  api --> svc
  svc --> jpa
  jpa --> db
  svc --> llm
  svc --> broker
  ws --> broker
```

## Modules

| Package | Responsibility |
|---------|----------------|
| `auth` | Registration, login, JWT issuance/validation |
| `user` | User aggregate and persistence |
| `exercise` | Exercise catalog (global + user-owned) |
| `workout` | Workouts and sets |
| `checkin` | Signed webhook check-ins |
| `websocket` | Live session event broadcasting |
| `notesimport` | Unstructured notes → structured workouts |
| `stats` | PRs, weekly stats, overload recommendations |
| `common` | Errors, correlation IDs, shared config |

## Cross-cutting concerns

- **Transactions** at the service layer (`@Transactional`)
- **Ownership** enforced in services (404 for inaccessible resources)
- **Events** published only after successful commit
- **External LLM** behind `WorkoutNotesParser` with a deterministic default

## Scalability note

The embedded STOMP simple broker is suitable for local/demo use. Production scale would replace it with an external broker (e.g. RabbitMQ STOMP) without changing domain services that publish through `SessionEventPublisher`.
