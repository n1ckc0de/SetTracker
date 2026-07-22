# Autonomous Project-Building Assignment

You are the principal software engineer responsible for designing, implementing, testing, documenting, and preparing this repository for GitHub.

Build a production-style portfolio project named **GymSync API**: a gym workout-tracking backend with real-time workout-session synchronization, webhook-driven gym check-ins, and AI-powered workout-note importing.

You have autonomy to inspect the repository, create and modify files, install appropriate project dependencies, run terminal commands, start local development services, execute tests, diagnose failures, and iterate until the project meets the definition of done.

Do not stop after scaffolding the application. Do not stop after producing a plan. Implement the working project.

## Operating Rules

1. Begin by inspecting the entire repository.
2. Determine what already exists before creating or replacing anything.
3. Create a concise implementation plan in `docs/IMPLEMENTATION_PLAN.md`.
4. Maintain a checklist in that file and update it as work is completed.
5. Work through the phases sequentially, but resolve foundational issues whenever necessary.
6. Run formatting, compilation, tests, and relevant integration checks after each meaningful phase.
7. When a command or test fails:
  - Read the complete error.
  - Identify the root cause.
  - Correct the implementation.
  - Run the command again.
  - Continue until it passes or an external dependency makes completion impossible.
8. Do not ask for routine implementation decisions. Choose sensible, conventional defaults and document them.
9. Ask for user input only when genuinely blocked by:
  - Missing credentials.
  - An irreversible or destructive action.
  - A paid external service.
  - A major product decision not answered by this specification.
10. Do not expose, invent, or commit secrets.
11. You may create `.env.example` and configuration placeholders, but never place real credentials in the repository.
12. Do not run destructive commands outside the project directory.
13. Do not globally install unnecessary software.
14. Do not deploy to a paid service without explicit authorization.
15. Never weaken tests merely to make them pass.
16. Never disable authentication, validation, security controls, static analysis, or failing tests simply to achieve a green build.
17. Do not leave essential functionality as TODO comments, pseudocode, mock methods, or empty implementations.
18. Continue until the definition of done has been checked systematically.
19. If an optional external service cannot be used because credentials are unavailable, build a working local abstraction and deterministic mock implementation so the application still runs and tests successfully.
20. Preserve Git history and commit-ready project quality. Do not rewrite or delete unrelated working code without justification.

---

# 1. Product Overview

Build a backend service that allows users to:

- Register and authenticate.
- Manage custom exercises.
- Start and complete workouts.
- Log, edit, and delete workout sets.
- View workout history.
- Watch active workout sessions update in real time.
- Receive gym check-in events through a secure webhook.
- Automatically start workouts from valid check-ins.
- Paste unstructured workout notes and convert them into structured workouts using an LLM.
- Review AI-imported workouts before confirming them.
- View personal records and weekly training statistics.
- Receive a simple progressive-overload recommendation for their next workout.

The project should demonstrate:

- Java fundamentals.
- Spring Boot architecture.
- REST API design.
- Spring Security and JWT authentication.
- PostgreSQL and Spring Data JPA.
- Transactions and concurrency management.
- Webhook validation and idempotency.
- WebSocket/STOMP communication.
- AI structured-output integration.
- Validation and error handling.
- Automated testing.
- Docker-based local development.
- GitHub-ready documentation.
- CI automation.
- Responsible handling of AI-generated data.

This is primarily a **backend API portfolio project**. Do not create a large frontend application unless all required backend phases are complete. Swagger/OpenAPI should serve as the primary interactive interface.

---

# 2. Required Technology Stack

Use stable, mutually compatible versions of:

- Java 21 LTS
- Spring Boot
- Maven
- PostgreSQL
- Spring Data JPA
- Spring Security
- JWT authentication
- Spring Validation
- Spring WebSocket
- STOMP
- Flyway for schema migrations
- springdoc-openapi for Swagger/OpenAPI
- Docker
- Docker Compose
- JUnit 5
- Mockito
- Spring Boot Test
- MockMvc
- Testcontainers for PostgreSQL integration tests
- JaCoCo for coverage reporting

Use Lombok only if it improves readability and does not obscure important domain behavior. Prefer explicit constructors and controlled entity methods over excessive annotation-generated behavior.

Use PostgreSQL in production-style and integration-test paths. H2 may only be used for extremely narrow unit tests, not as a substitute for PostgreSQL integration testing.

---

# 3. Architecture

Use a modular monolith with clear package boundaries.

A suitable starting structure is:

```text
src/main/java/<base-package>/
  auth/
    controller/
    dto/
    security/
    service/
  user/
    domain/
    repository/
    service/
  exercise/
    controller/
    domain/
    dto/
    repository/
    service/
  workout/
    controller/
    domain/
    dto/
    repository/
    service/
  checkin/
    controller/
    domain/
    dto/
    repository/
    service/
  websocket/
    config/
    dto/
    service/
  notesimport/
    controller/
    domain/
    dto/
    llm/
    service/
  stats/
    controller/
    dto/
    service/
  common/
    config/
    exception/
    response/
    validation/

```

Do not create layers mechanically when they add no value, but maintain these responsibilities:

- Controllers handle HTTP concerns.
- Services coordinate business operations.
- Repositories handle persistence.
- DTOs define external request and response contracts.
- Entities model persisted domain state.
- Mappers translate between entities and DTOs.
- Security code remains isolated from ordinary business logic.
- External LLM integration is behind an interface.
- WebSocket broadcasting is behind a service abstraction.

Do not expose JPA entities directly from controllers.

Avoid circular dependencies.

Prefer constructor injection.

Use transactions at the service layer.

---

# 4. Domain Model

Use UUID primary keys.

All timestamps should be stored consistently using UTC-compatible types such as `Instant`.

## User

Fields:

- `id: UUID`
- `email: String`, unique and normalized
- `passwordHash: String`
- `name: String`
- `role: enum`
- `createdAt: Instant`
- `updatedAt: Instant`

Roles:

- `USER`
- `ADMIN`

Requirements:

- Store BCrypt password hashes only.
- Never return `passwordHash`.
- Normalize email addresses before uniqueness checks.
- Enforce database-level uniqueness.
- Include appropriate validation constraints.

## Exercise

Fields:

- `id: UUID`
- `name: String`
- `normalizedName: String`
- `category: ExerciseCategory`
- `createdBy: User`, nullable
- `createdAt: Instant`
- `updatedAt: Instant`

Categories:

- `CHEST`
- `BACK`
- `LEGS`
- `ARMS`
- `SHOULDERS`
- `CORE`
- `CARDIO`
- `FULL_BODY`
- `OTHER`

Rules:

- `createdBy = null` means a global/default exercise.
- Ordinary users may read global exercises and their own exercises.
- Ordinary users may update or delete only exercises they created.
- Exercise names should be normalized to help matching and duplicate detection.
- Do not allow users to access another user's custom exercise through direct ID manipulation.

## Workout

Fields:

- `id: UUID`
- `user: User`
- `startedAt: Instant`
- `endedAt: Instant`, nullable
- `status: WorkoutStatus`
- `source: WorkoutSource`
- `importStatus: ImportStatus`
- `createdAt: Instant`
- `updatedAt: Instant`
- optional `version` field for optimistic locking

Statuses:

- `ACTIVE`
- `COMPLETED`
- `CANCELLED`

Sources:

- `MANUAL`
- `WEBHOOK`
- `NOTES_IMPORT`

Import statuses:

- `NOT_APPLICABLE`
- `PENDING_REVIEW`
- `CONFIRMED`
- `REJECTED`

Rules:

- A manually started workout begins as `ACTIVE`.
- A normal completed workout must have `endedAt`.
- A workout cannot be completed twice.
- A completed workout cannot receive new sets unless explicitly reopened by a supported business operation.
- An AI-imported workout begins in `PENDING_REVIEW`.
- Imported workouts should not count toward official statistics until confirmed.
- Decide whether a user may have more than one active workout. Prefer one active workout per user unless a documented business reason supports otherwise.
- Enforce the active-workout rule safely under concurrent requests.

## WorkoutSet

Do not name the Java entity simply `Set`, because that conflicts conceptually with `java.util.Set`.

Fields:

- `id: UUID`
- `workout: Workout`
- `exercise: Exercise`
- `weight: BigDecimal`
- `reps: Integer`
- `setNumber: Integer`
- `loggedAt: Instant`
- `createdAt: Instant`
- `updatedAt: Instant`
- optional `version` field for optimistic locking

Rules:

- Weight must be non-negative.
- Repetitions must be positive.
- Set number must be positive.
- The exercise must be visible to the workout owner.
- Only the workout owner may modify its sets.
- Set order must be deterministic.
- Use `BigDecimal` appropriately and specify precision and scale.

## CheckInEvent

Fields:

- `id: UUID`
- `externalEventId: String`
- `user: User`
- `source: String`
- `receivedAt: Instant`
- `triggeredWorkout: Workout`, nullable
- `status: CheckInStatus`
- `failureReason: String`, nullable

Statuses may include:

- `RECEIVED`
- `PROCESSED`
- `DUPLICATE`
- `REJECTED`
- `FAILED`

Requirements:

- `externalEventId` must support idempotency.
- The same webhook event must not create multiple workouts.
- Record enough information to audit webhook processing without storing sensitive secrets.

---

# 5. Database Requirements

Use Flyway migrations from the beginning.

Create:

- Tables.
- Primary keys.
- Foreign keys.
- Unique constraints.
- Check constraints where appropriate.
- Useful indexes.
- Audit timestamps.
- Schema changes through versioned migrations.

At minimum, consider indexes for:

- User email.
- Exercise normalized name.
- Exercise creator.
- Workout user and start time.
- Workout user and status.
- Workout-set workout and logged time.
- Check-in external event ID.
- Check-in user and received time.

Avoid relying only on application-level validation when the database can safely enforce an invariant.

Seed a small set of default exercises through a migration or clearly controlled startup data loader. The seed operation must be repeatable and safe.

Suggested defaults:

- Bench Press
- Squat
- Deadlift
- Overhead Press
- Barbell Row
- Pull-Up
- Lat Pulldown
- Leg Press
- Dumbbell Curl
- Triceps Pushdown
- Plank
- Running

---

# 6. Authentication and Authorization

Implement:

```text
POST /api/auth/register
POST /api/auth/login

```

Registration request:

```json
{
  "email": "user@example.com",
  "password": "strong-password",
  "name": "Example User"
}

```

Login returns:

- Access token.
- Token type.
- Expiration information.
- Minimal user information.

Requirements:

- BCrypt password hashing.
- Stateless JWT authentication.
- Configurable JWT secret and expiration.
- JWT secret loaded from environment configuration.
- Reject weak or malformed registration input.
- Do not reveal whether an email exists through unnecessarily detailed authentication errors.
- Secure every non-public endpoint.
- Enforce object ownership in service or authorization logic.
- Return `401` for unauthenticated requests.
- Return `403` for authenticated users lacking permission.
- Add tests for cross-user access attempts.
- Configure CORS through externalized properties.
- Do not use wildcard CORS with credentials.
- Document the development configuration.

Do not log passwords, JWTs, webhook secrets, authorization headers, or LLM API keys.

---

# 7. Exercise REST API

Implement:

```text
GET    /api/exercises
POST   /api/exercises
GET    /api/exercises/{exerciseId}
PUT    /api/exercises/{exerciseId}
DELETE /api/exercises/{exerciseId}

```

`GET /api/exercises` should support:

- Pagination.
- Search by name.
- Filtering by category.
- Sorting.
- Global plus current-user custom exercises.

Example:

```text
GET /api/exercises?query=bench&category=CHEST&page=0&size=20&sort=name,asc

```

Use clear request and response DTOs.

Return appropriate statuses:

- `200` for successful reads and updates.
- `201` for creation.
- `204` for successful deletion.
- `400` for validation errors.
- `401` and `403` for authentication and authorization failures.
- `404` for missing or inaccessible resources.
- `409` for relevant uniqueness or state conflicts.

---

# 8. Workout REST API

Implement:

```text
POST   /api/workouts
GET    /api/workouts
GET    /api/workouts/{workoutId}
PATCH  /api/workouts/{workoutId}/complete
DELETE /api/workouts/{workoutId}

```

Workout history must support:

- Pagination.
- Status filtering.
- Source filtering.
- Date-range filtering.
- Newest-first default ordering.

Example:

```text
GET /api/workouts?status=COMPLETED&source=MANUAL&from=2026-01-01&to=2026-07-01&page=0&size=20

```

`GET /api/workouts/{workoutId}` should return:

- Workout metadata.
- Associated sets.
- Exercise information required to display each set.
- Import-review status when relevant.

Completing a workout must:

- Verify ownership.
- Verify the workout is active.
- Set `endedAt`.
- Transition status transactionally.
- Return the completed representation.
- Broadcast a session-completed WebSocket event only after the database commit succeeds.

Deleting a workout should use clearly documented behavior. Choose either hard deletion or soft cancellation and explain the trade-off. Prefer cancellation or controlled deletion when historical integrity matters.

---

# 9. Workout-Set REST API

Implement:

```text
POST   /api/workouts/{workoutId}/sets
GET    /api/workouts/{workoutId}/sets
PUT    /api/sets/{setId}
DELETE /api/sets/{setId}

```

Logging a set must:

1. Authenticate the user.
2. Find the workout under the authenticated user's ownership.
3. Verify the workout is active or is an imported workout pending review.
4. Validate the exercise is visible to the user.
5. Validate weight, repetitions, and set number.
6. Persist the set transactionally.
7. Commit the transaction.
8. Broadcast the created set after commit.
9. Return `201 Created`.

Prevent duplicate or conflicting set numbers for the same exercise within the same workout where appropriate. Document the chosen behavior.

Create explicit tests for:

- Valid set creation.
- Missing workout.
- Completed workout.
- Another user's workout.
- Invalid exercise.
- Negative weight.
- Zero repetitions.
- Concurrent updates where applicable.

---

# 10. Error Handling

Implement centralized exception handling with `@RestControllerAdvice`.

Use a consistent error contract similar to:

```json
{
  "timestamp": "2026-07-17T20:30:00Z",
  "status": 400,
  "error": "Validation Failed",
  "code": "VALIDATION_ERROR",
  "message": "One or more fields are invalid.",
  "path": "/api/workouts",
  "fieldErrors": {
    "reps": "must be greater than zero"
  },
  "correlationId": "..."
}

```

Requirements:

- Stable machine-readable error codes.
- Friendly messages without exposing stack traces.
- Field-level validation errors.
- Conflict errors for invalid state transitions.
- Access-denied responses.
- Invalid-JWT responses.
- Malformed-JSON responses.
- Unknown endpoint handling where practical.
- Correlation IDs for tracing requests.
- Complete server-side logging at suitable levels.

---

# 11. WebSocket Live Sessions

Configure a STOMP-over-WebSocket endpoint:

```text
/ws/sessions

```

Support subscription destinations such as:

```text
/topic/session/{workoutId}
/topic/user/{userId}/sessions

```

Define typed event envelopes:

```json
{
  "eventId": "uuid",
  "eventType": "SET_LOGGED",
  "occurredAt": "2026-07-17T20:30:00Z",
  "workoutId": "uuid",
  "data": {}
}

```

Event types should include at least:

- `SESSION_STARTED`
- `SET_LOGGED`
- `SET_UPDATED`
- `SET_DELETED`
- `SESSION_COMPLETED`

Requirements:

- Broadcast only after the related database transaction commits.
- Do not publish an event for rolled-back operations.
- Prevent unauthorized users from casually subscribing to other users' private workout data.
- Document the chosen WebSocket authentication strategy.
- Handle connection and disconnection events appropriately.
- Keep payloads versionable and stable.
- Add tests for event production and destination selection.
- Provide a simple manual WebSocket testing guide.

A full distributed message broker is not required for the first version. Use Spring's simple broker locally, document its limitations, and describe how RabbitMQ or another broker could replace it at scale.

---

# 12. Secure Check-In Webhook

Implement:

```text
POST /api/webhooks/checkin

```

Suggested request:

```json
{
  "eventId": "turnstile-event-12345",
  "userId": "uuid",
  "source": "gym-turnstile-mock",
  "occurredAt": "2026-07-17T20:30:00Z"
}

```

Do not rely only on an ordinary static header comparison if a stronger implementation is feasible.

Prefer HMAC signature validation using:

- A configurable shared secret.
- The raw request body.
- A timestamp header.
- A signature header.
- A constant-time comparison.
- A permitted timestamp tolerance to reduce replay attacks.

For example:

```text
X-Webhook-Timestamp
X-Webhook-Signature

```

Requirements:

- Validate the signature.
- Reject stale timestamps.
- Require an external event ID.
- Make processing idempotent.
- Store the event.
- Create a new active workout only when allowed.
- Link the check-in event to the created workout.
- Broadcast `SESSION_STARTED` after commit.
- Return a safe response for duplicate delivery.
- Avoid creating two workouts if the provider retries.
- Decide what happens when the user already has an active workout.
- Document retry behavior.
- Include a script or test utility that creates a correctly signed mock request.

Add tests for:

- Valid signature.
- Invalid signature.
- Missing signature.
- Expired timestamp.
- Duplicate event.
- Unknown user.
- Existing active workout.
- Database failure behavior.
- Successful workout creation.

---

# 13. AI Workout-Notes Import

Implement:

```text
POST  /api/notes/import
PATCH /api/workouts/{workoutId}/confirm-import
PATCH /api/workouts/{workoutId}/reject-import

```

Import request:

```json
{
  "rawText": "Bench 3x8 135 lb, Squat 5x5 185 lb"
}

```

The LLM pipeline should:

1. Validate the input length and content.
2. Send a carefully constrained structured-output request.
3. Require a schema containing exercises and sets.
4. Parse the response safely.
5. Reject malformed or incomplete responses.
6. Normalize exercise names.
7. Match known exercises.
8. Surface ambiguous matches.
9. Create user-owned exercises only according to clearly documented behavior.
10. Create a workout with:
  - `source = NOTES_IMPORT`
  - `importStatus = PENDING_REVIEW`
11. Create the parsed sets.
12. Return the pending workout for human review.
13. Exclude the workout from official statistics until confirmation.

Define an interface such as:

```java
public interface WorkoutNotesParser {
    ParsedWorkout parse(String rawText);
}

```

Provide at least two implementations:

- A real provider-backed implementation enabled only when configured.
- A deterministic mock or rule-based parser for local development and tests.

The application must start and its test suite must pass without a paid LLM account.

Externalize:

- Provider selection.
- Model name.
- API URL where applicable.
- API key.
- Timeout.
- Retry count.

Security and reliability requirements:

- Never commit or log API keys.
- Apply timeouts.
- Limit retries.
- Avoid retrying invalid requests.
- Validate all LLM output as untrusted input.
- Place limits on imported exercises and sets.
- Preserve the original raw text where appropriate for review, but assess privacy implications.
- Do not blindly create official workout history.
- Return meaningful errors if the provider is unavailable.
- Add unit tests around malformed output, hallucinated fields, ambiguous exercises, and provider failures.

Add an LLM prompt template that requests strict structured output. Keep the template in a maintainable location and test its expected schema.

---

# 14. Personal Records

Implement:

```text
GET /api/exercises/{exerciseId}/pr

```

Return useful records based only on confirmed, completed workouts:

- Heaviest weight.
- Highest repetitions at a given weight where practical.
- Estimated one-repetition maximum using a documented formula.
- Date and workout associated with the record.

Do not claim medical or professional training accuracy.

Add indexes or efficient queries rather than loading the user's entire workout history into memory.

Caching is optional. If caching is added:

- Use a clear cache key.
- Invalidate it when relevant sets or workouts change.
- Document consistency trade-offs.
- Include tests.

---

# 15. Weekly Statistics

Implement:

```text
GET /api/stats/weekly

```

Support a date or week parameter.

Return:

- Number of completed workouts.
- Total sets.
- Total repetitions.
- Total volume.
- Volume by exercise.
- Volume by category.
- Comparison with the prior week where possible.

Define volume clearly:

```text
volume = weight × repetitions

```

Address body-weight or cardio exercises explicitly rather than silently producing misleading values.

Only confirmed, completed workouts count.

Use database aggregation when reasonable.

---

# 16. Progressive-Overload Recommendation

Add an endpoint such as:

```text
GET /api/exercises/{exerciseId}/next-session-recommendation

```

For a simple first version, use deterministic business rules—not an LLM.

Example logic:

- If the user completed the top of the target repetition range with good consistency, recommend a small weight increase and a lower repetition target.
- Otherwise, recommend keeping the weight and adding repetitions.
- Avoid recommending negative weights or unrealistic jumps.
- Respect configurable increments.
- State that the recommendation is informational and not medical advice.

Example response:

```json
{
  "exerciseId": "uuid",
  "basedOnWorkoutId": "uuid",
  "previousPerformance": {
    "weight": 135,
    "reps": 10
  },
  "recommendation": {
    "optionA": {
      "weight": 140,
      "targetReps": 8
    },
    "optionB": {
      "weight": 135,
      "targetReps": 11
    }
  },
  "reason": "You reached the upper end of the configured repetition range."
}

```

Keep the recommendation engine behind an interface so a more advanced implementation can replace it later.

Add tests around boundaries and missing history.

---

# 17. AI Personal-Trainer Architecture

Do not build an unsafe autonomous medical coach.

Design a limited assistant interface that can summarize information already present in the user's workout history.

Potential future capabilities:

- Workout-duration summary.
- Strength-trend summary.
- Recently improved exercises.
- General hydration reminder.
- User-configured supplement or vitamin reminders.
- Suggested questions to discuss with a qualified trainer.

Do not:

- Diagnose injuries.
- Prescribe supplements.
- Make medical claims.
- Recommend dangerous exercise loads.
- Infer health conditions.
- Present generated content as professional medical advice.

For this version, either:

1. Implement a clearly labeled experimental summary endpoint behind the existing LLM abstraction, or
2. Produce a complete architecture/design document if core functionality is not yet fully complete.

Core application quality takes priority over this stretch feature.

---

# 18. Equipment-Photo Workout Generation

Treat this as a future architectural extension unless every required backend feature is complete and fully tested.

Create a design document covering:

- Image upload flow.
- Object storage.
- Image-type and size validation.
- Malware scanning considerations.
- Vision-model integration.
- Confidence scores.
- Human confirmation of detected equipment.
- Exercise recommendation rules.
- Experience-level input.
- Safety disclaimers.
- Curated form media rather than hallucinated links.
- Privacy and image-retention policies.
- Asynchronous job processing.

Do not fabricate exercise videos or copyrighted instructional media.

Document a proposed endpoint contract, but do not let this optional feature delay completion of the core API.

---

# 19. Concurrency and Transaction Design

Explicitly handle race conditions for:

- Two simultaneous attempts to create an active workout.
- Duplicate webhook deliveries.
- Concurrent completion of the same workout.
- Updating a set while its workout is being completed.
- Multiple note-import confirmation requests.
- Simultaneous updates to personal-record data.

Use appropriate combinations of:

- Database uniqueness constraints.
- Transactions.
- Optimistic locking with `@Version`.
- Pessimistic locking where justified.
- Idempotency keys.
- Retry behavior for transient conflicts.

Do not use in-memory locking as the primary guarantee.

Create `docs/CONCURRENCY.md` explaining:

- Each identified race condition.
- The mechanism used to prevent it.
- The trade-offs.
- Relevant tests.

---

# 20. Observability

Implement:

- Structured application logging.
- Correlation/request IDs.
- Appropriate log levels.
- Spring Boot Actuator health endpoint.
- Database health visibility.
- Safe startup diagnostics.
- No sensitive-data logging.

Consider metrics for:

- API request counts.
- API latency.
- Webhook accepted/rejected/duplicate counts.
- LLM parse successes and failures.
- Active WebSocket connections.

Use Micrometer-compatible instrumentation where practical.

Do not expose sensitive actuator endpoints publicly.

---

# 21. API Documentation

Configure Swagger/OpenAPI.

Requirements:

- Document authentication.
- Document request and response schemas.
- Document validation constraints.
- Document common error responses.
- Group endpoints logically.
- Include example payloads.
- Make local Swagger UI accessible.
- Document how to authorize with a JWT.
- Document the webhook headers and signing process.
- Document WebSocket destinations separately if OpenAPI cannot express them adequately.

Export or generate an OpenAPI specification as part of the project where practical.

---

# 22. Testing Strategy

Implement meaningful automated tests at several levels.

## Unit tests

Cover:

- Authentication business logic.
- Workout state transitions.
- Progressive-overload rules.
- LLM output validation.
- HMAC verification.
- Exercise normalization and matching.
- Statistics calculations.

## Controller tests

Use MockMvc or an equivalent Spring testing approach to cover:

- Successful requests.
- Authentication failures.
- Authorization failures.
- Validation failures.
- Missing resources.
- State conflicts.
- Response shapes.

## Integration tests

Use Testcontainers with PostgreSQL for:

- Repository behavior.
- Unique constraints.
- Flyway migrations.
- JWT-protected workflows.
- Webhook idempotency.
- Workout creation and completion.
- AI-import persistence.
- Ownership boundaries.
- Concurrency-sensitive operations where feasible.

Create at least one end-to-end integration flow:

1. Register a user.
2. Log in.
3. Create or select an exercise.
4. Start a workout.
5. Add sets.
6. Complete the workout.
7. Retrieve workout history.
8. Retrieve statistics.
9. Retrieve a recommendation.

Create another flow for:

1. Receive a signed check-in.
2. Create an active workout.
3. Retry the same event.
4. Verify only one workout exists.

Create another flow for:

1. Import raw notes through the deterministic parser.
2. Review the pending workout.
3. Confirm it.
4. Verify it begins counting toward statistics.

Set a reasonable coverage threshold, prioritizing business logic rather than chasing meaningless line coverage.

---

# 23. Code Quality

Configure:

- Consistent formatting.
- Static analysis or linting suitable for Java.
- Compiler warnings where practical.
- JaCoCo reporting.
- Maven verification.
- Dependency vulnerability scanning where reasonable.

Avoid:

- Giant service classes.
- Controllers containing business logic.
- Generic `RuntimeException` for expected domain errors.
- Catching and ignoring exceptions.
- Hardcoded secrets.
- Unbounded API input.
- N+1 query behavior.
- Returning lazy JPA entities directly.
- Excessive bidirectional entity relationships.
- Hidden database access during serialization.
- Duplicate DTOs without purpose.
- Premature microservices.

Run:

```text
./mvnw clean verify

```

The final repository must pass this command.

---

# 24. Docker and Local Development

Provide:

- `Dockerfile`
- `compose.yaml`
- PostgreSQL service
- Application service where practical
- Health checks
- Persistent local database volume
- Environment variable configuration
- `.env.example`

Support a straightforward startup command such as:

```text
docker compose up --build

```

The README must also explain how to run:

- The database through Docker while running Spring Boot locally.
- The entire stack through Docker Compose.
- Tests independently.

Use a non-root application user in the production-style Docker image where practical.

Use a multi-stage build.

Do not bake secrets into images.

---

# 25. Developer Utilities

Create helpful, safe tooling such as:

```text
scripts/
  sign-checkin-webhook.sh
  smoke-test.sh
  start-local.sh

```

Scripts must:

- Fail clearly.
- Be documented.
- Avoid machine-specific assumptions where possible.
- Not contain credentials.
- Work on a common shell environment or provide platform alternatives.

Also provide an API request collection in one of these forms:

- Bruno
- Postman
- HTTP files
- cURL examples

Prefer a repository-friendly, text-based format.

---

# 26. CI/CD

Create a GitHub Actions workflow that runs on pushes and pull requests.

It should:

- Set up Java.
- Cache Maven dependencies.
- Compile.
- Run tests.
- Run integration tests.
- Run formatting/static checks.
- Generate coverage.
- Build the application artifact.
- Optionally build the Docker image without pushing it.

Do not deploy automatically.

Use least-privilege GitHub Actions permissions.

Pin action versions responsibly.

Add badges to the README only when they correspond to real workflows.

---

# 27. README

Create a polished, GitHub-ready `README.md`.

It must include:

1. Project title.
2. Clear one-paragraph summary.
3. Current status:
  - Explicitly state that the project is under active development.
4. Purpose:
  - Explain that it is both a useful workout API and a demonstration of backend engineering concepts.
5. Key features.
6. Architecture overview.
7. Technology stack.
8. Domain model summary.
9. API endpoint summary.
10. Authentication instructions.
11. Webhook-signing instructions.
12. WebSocket usage.
13. AI notes-import explanation.
14. Human-review and data-quality approach.
15. Local installation.
16. Docker instructions.
17. Environment variables.
18. Database migrations.
19. Testing instructions.
20. Swagger/OpenAPI instructions.
21. Example API workflow.
22. Project structure.
23. Security considerations.
24. Concurrency considerations.
25. Known limitations.
26. Roadmap.
27. Future advancements.
28. Contribution guidelines.
29. License.
30. Author placeholders.

Include a Mermaid architecture diagram if GitHub can render it correctly.

Include example requests and responses, but do not overload the README with every implementation detail. Link to focused documents under `docs/`.

The roadmap should distinguish:

- Completed.
- In progress.
- Planned.
- Exploratory.

Do not claim that an unfinished feature is complete.

---

# 28. Additional Documentation

Create:

```text
docs/
  IMPLEMENTATION_PLAN.md
  ARCHITECTURE.md
  API_DESIGN.md
  SECURITY.md
  CONCURRENCY.md
  WEBHOOKS.md
  WEBSOCKETS.md
  AI_NOTES_IMPORT.md
  TESTING.md
  DECISIONS.md
  FUTURE_ROADMAP.md

```

`DECISIONS.md` should contain lightweight architecture-decision records covering major choices such as:

- Modular monolith versus microservices.
- PostgreSQL.
- JWT.
- Flyway.
- STOMP/WebSocket.
- HMAC webhook security.
- Transaction strategy.
- LLM abstraction and fallback parser.
- Pending-review imports.
- Deterministic overload recommendations.

---

# 29. Git Repository Quality

Create or improve:

- `.gitignore`
- `.gitattributes`
- `.editorconfig`
- `.env.example`
- `LICENSE`
- Contribution instructions
- Pull-request template
- Issue templates where useful
- Dependabot configuration where appropriate

Use a suitable open-source license, preferably MIT unless an existing license or repository requirement says otherwise.

Check the repository for:

- Secrets.
- Generated artifacts.
- IDE-specific files.
- Debug logs.
- Dead code.
- Placeholder implementation.
- Accidental database files.
- Unused dependencies.
- Unused imports.
- Unresolved TODOs.
- Misleading documentation.

Do not make up the author's name, GitHub URL, LinkedIn URL, screenshots, deployment URL, or CI status. Use explicit placeholders when information is unavailable.

---

# 30. Build Phases

Follow this order.

## Phase 0: Audit and planning

- Inspect repository.
- Record current state.
- Create implementation plan.
- Identify blockers.
- Establish baseline build.

## Phase 1: Foundation

- Spring Boot project.
- Maven wrapper.
- PostgreSQL configuration.
- Docker Compose.
- Flyway.
- Base exception handling.
- Core domain entities.
- Repositories.
- Seed exercises.

## Phase 2: Authentication

- Registration.
- Login.
- JWT.
- Ownership security.
- Security tests.

## Phase 3: Core REST API

- Exercise CRUD.
- Workout CRUD.
- Workout-set CRUD.
- Pagination and filtering.
- Validation.
- OpenAPI documentation.
- Tests.

## Phase 4: Webhook check-ins

- Signature validation.
- Replay protection.
- Idempotency.
- Workout creation.
- Audit record.
- Tests and signing script.

## Phase 5: Live WebSocket sessions

- STOMP configuration.
- Authentication strategy.
- Domain events.
- Post-commit publishing.
- Tests and usage documentation.

## Phase 6: AI notes import

- Provider-neutral parser interface.
- Deterministic local parser.
- Optional configured LLM implementation.
- Structured-output validation.
- Exercise matching.
- Pending review.
- Confirm and reject flows.
- Tests.

## Phase 7: Statistics and recommendations

- Personal records.
- Weekly statistics.
- Progressive-overload recommendations.
- Efficient queries.
- Tests.

## Phase 8: Hardening

- Concurrency testing.
- Security review.
- Logging.
- Actuator.
- Performance review.
- N+1 query review.
- Dependency cleanup.
- Full test suite.

## Phase 9: GitHub readiness

- README.
- Architecture documentation.
- CI.
- API collection.
- License.
- Repository templates.
- Final verification.

Optional AI-trainer and equipment-image features must not begin until Phases 0–9 are complete.

---

# 31. Progress Protocol

After each phase:

1. Update `docs/IMPLEMENTATION_PLAN.md`.
2. Summarize completed work in the agent log.
3. Run the relevant focused tests.
4. Run:

```text
./mvnw test

```

1. At major milestones, run:

```text
./mvnw clean verify

```

1. Check Docker startup when infrastructure changed.
2. Correct failures before moving forward.
3. Note architectural decisions in `docs/DECISIONS.md`.
4. Continue automatically to the next phase.

Do not pause merely to announce that a phase is complete.

Do not ask permission to continue to the next ordinary phase.

If context becomes constrained, first update the implementation plan with:

- Work completed.
- Current build status.
- Remaining tasks.
- Exact next action.
- Known failures.

Then continue from that durable project state.

---

# 32. Definition of Done

The project is complete only when all applicable items below are true:

- The application compiles.
- PostgreSQL migrations apply successfully.
- Docker-based local development works.
- Registration and login work.
- JWT security works.
- Object ownership is enforced.
- Exercise CRUD works.
- Workout CRUD works.
- Workout-set CRUD works.
- Workout completion works.
- Webhook validation works.
- Duplicate webhook delivery does not duplicate workouts.
- WebSocket events are emitted after successful commits.
- Notes can be imported without requiring a paid API.
- A configured LLM provider can be plugged in cleanly.
- Imported workouts require review.
- Import confirmation and rejection work.
- Personal records work.
- Weekly statistics work.
- Progressive-overload recommendations work.
- Validation produces consistent errors.
- Cross-user access is tested.
- Concurrency-sensitive operations have protections and tests.
- OpenAPI documentation works.
- Actuator health checks work safely.
- Unit tests pass.
- Integration tests pass.
- `./mvnw clean verify` passes.
- The Docker image builds.
- GitHub Actions configuration is present and coherent.
- No secrets are committed.
- No required functionality remains as pseudocode.
- The README accurately reflects implementation status.
- The roadmap clearly labels unfinished features.
- The repository is suitable for public GitHub review.

---

# 33. Final Audit

When implementation is complete:

1. Review the repository as a senior Java engineer.
2. Review it as an application-security engineer.
3. Review it as a technical interviewer evaluating a junior engineer.
4. Review it as an open-source user attempting to run the project.
5. Fix all critical and high-priority findings.
6. Run the complete verification suite again.
7. Compare the implementation against every section of this specification.
8. Update documentation so it reflects reality.
9. Produce a final report containing:
  - What was built.
  - Architecture summary.
  - Commands run.
  - Test results.
  - Coverage summary.
  - Security controls.
  - Known limitations.
  - Features intentionally deferred.
  - Recommended next steps.

Do not declare completion unless the build and required tests pass.

If an external credential is the only remaining blocker, complete all local abstractions, mocks, tests, documentation, and configuration examples, and state precisely what credential-dependent behavior remains unverified.