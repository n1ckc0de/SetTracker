# GymSync API — Decisions Log

Short, dated entries for notable design/implementation decisions. See `PROJECT_SPEC.md` for the
full spec and `docs/IMPLEMENTATION_PLAN.md` for phase status.

## 2026-07-18 — Modular monolith (not microservices)

GymSync is a single deployable Spring Boot application with package boundaries (`auth`,
`exercise`, `workout`, `checkin`, `websocket`, `notesimport`, `stats`, `common`). A modular
monolith keeps transactional workflows (check-in → workout → WebSocket event) simple, avoids
distributed-tracing overhead for a portfolio backend, and still demonstrates clear separation of
concerns. Microservices would add operational cost without a multi-team scaling need.

## 2026-07-18 — PostgreSQL as the system of record

PostgreSQL provides partial unique indexes (one active workout per user), check constraints, and
strong transactional semantics required by webhook idempotency and optimistic locking. H2 is not
used for integration tests; Testcontainers runs real Postgres so Flyway migrations and constraints
are verified against production-like behavior.

## 2026-07-18 — Stateless JWT authentication

Registration/login issue a signed JWT (HMAC, secret from env). The API is stateless — no server
session store — which fits horizontal scaling and a Swagger-first workflow. Tokens carry the user
id; ownership checks happen in services. Webhook and health endpoints remain non-JWT and use
their own auth (HMAC / public health only).

## 2026-07-18 — Flyway for schema evolution

All schema changes ship as versioned SQL under `src/main/resources/db/migration`. Hibernate
`ddl-auto` is `validate` only. This keeps production, Docker, and Testcontainers on the same
migration path and makes reviewable diffs for indexes and constraints.

## 2026-07-18 — STOMP over WebSocket with a simple broker

Live session updates use STOMP at `/ws/sessions` with destinations
`/topic/session/{workoutId}` and `/topic/user/{userId}/sessions`. Spring's simple in-memory
broker is enough for local/single-instance demos. Limitations (no multi-instance fan-out) are
documented in `docs/WEBSOCKETS.md`; RabbitMQ/Redis STOMP relay is the documented scale-out path.

## 2026-07-18 — HMAC webhook security

Check-ins require `X-Webhook-Timestamp` + `X-Webhook-Signature` over the raw body, constant-time
compare, and a configurable timestamp skew window to limit replay. A static shared secret is
externalized via env — stronger than a bare API key header, sufficient without introducing a full
mTLS mesh for this portfolio scope. See `docs/WEBHOOKS.md`.

## 2026-07-18 — Transaction strategy and post-commit side effects

Business writes run in `@Transactional` service methods. WebSocket publishes go through
`TransactionUtils.runAfterCommit` so rolled-back work never broadcasts. Optimistic `@Version`
handles low-contention races (complete/confirm); uniqueness constraints handle high-correctness
invariants (active workout, webhook event id). Details in `docs/CONCURRENCY.md`.

## 2026-07-18 — LLM abstraction with rule-based fallback

`WorkoutNotesParser` has a deterministic rule-based default and an optional OpenAI-compatible
implementation gated by `gymsync.llm.provider`. Tests and local runs never require a paid key.
All model output is validated as untrusted structured data before persistence. See
`docs/AI_NOTES_IMPORT.md`.

## 2026-07-18 — Pending-review imports before official history

Notes-imported workouts start as `COMPLETED` + `PENDING_REVIEW` and are excluded from PRs,
weekly stats, and recommendations until `confirm-import`. This prevents unverified LLM/parser
output from polluting training history while still letting users edit sets during review.

## 2026-07-18 — Deterministic progressive-overload recommendations

`GET /api/exercises/{id}/next-session-recommendation` uses configurable rep ranges and weight
increments (`OverloadRecommendationEngine`) — not an LLM — so results are testable, explainable,
and free of medical-sounding generative claims. Responses always include an informational
disclaimer.

## 2026-07-18 — One active workout per user

A user may have at most one `ACTIVE` workout at a time. This is enforced twice:

- **Service pre-check:** `WorkoutService.startWorkout` looks up an existing `ACTIVE` workout for
  the user and throws `ConflictException` (409) before attempting an insert.
- **Database constraint (source of truth):** a unique partial index on
  `workouts(user_id) WHERE status = 'ACTIVE'` rejects a second active workout even under
  concurrent requests that both pass the service pre-check. `WorkoutService` catches
  `DataIntegrityViolationException` from the race and converts it to the same 409.

The pre-check exists purely for a fast, friendly error path; the constraint is what actually
guarantees the invariant under concurrency.

## 2026-07-18 — Soft-cancel instead of hard delete for workouts

`DELETE /api/workouts/{id}` does not remove the row. It transitions the workout to `CANCELLED`
(`WorkoutService.cancelWorkout`). Reasons:

- Cancelled workouts (e.g. a check-in webhook that spuriously started a session) still carry
  useful audit/history information.
- Hard deletion would either orphan or cascade-delete any `WorkoutSet` rows already logged
  against that workout, silently losing data a user may have already entered.
- Completed workouts cannot be cancelled (409) — cancellation is only for workouts that never
  finished.

Cancelling an already-cancelled workout is a no-op (idempotent `DELETE`).

## 2026-07-18 — Unique set number per exercise per workout

`WorkoutSet` uniqueness is scoped to `(workout_id, exercise_id, set_number)`, not just
`(workout_id, set_number)`, since a workout is expected to interleave sets across multiple
exercises (e.g. bench press set 1, squat set 1, bench press set 2). Enforced by:

- A service-level check (`existsByWorkoutAndExerciseAndSetNumber[AndIdNot]`) before insert/update,
  returning `ConflictException` (409).
- A matching unique database constraint as the concurrency-safe fallback, with
  `DataIntegrityViolationException` mapped to the same 409 by `GlobalExceptionHandler`.

## 2026-07-18 — Optional list filters via JPA Specifications, not `:param IS NULL OR ...` JPQL

`GET /api/workouts` (status/source/from/to) and `GET /api/exercises` (query/category) support
several optional filters. The first pass used a single static `@Query` per endpoint with
`(:param IS NULL OR entity.field = :param)` clauses so one query could serve every filter
combination.

Under Postgres, several of those parameters were only ever compared via `IS NULL` when a filter
was absent (e.g. `WorkoutRepository`'s `from`/`to` on the "no date filter" request), and Postgres'
extended query protocol failed to infer a bind type for a parameter used *only* that way:

```
ERROR: could not determine data type of parameter $6
```

Wrapping the parameter in an explicit `CAST(:from AS timestamp)` avoided that error but then hit a
second Postgres quirk — Hibernate bound the still-null parameter as `bytea`, and Postgres has no
implicit cast from `bytea` to `timestamp`:

```
ERROR: cannot cast type bytea to timestamp without time zone
```

Rather than keep patching per-type casts, `WorkoutRepository` now extends
`JpaSpecificationExecutor<Workout>` and `WorkoutService` composes `Specification<Workout>`
predicates via `WorkoutSpecifications`, using `Specification.allOf(...)` (which is null-tolerant —
`null` entries contribute no predicate). A filter that isn't supplied simply never appears in the
generated SQL, so there's no ambiguous null parameter for Postgres to type-infer in the first
place. `ExerciseRepository.findVisibleExercises` keeps its single `@Query` (its only ambiguous
parameter was the free-text `query`, fixed with `CAST(:query AS string)`), since it doesn't hit
the same class of failure across all of its filters.

## 2026-07-18 — Testcontainers Postgres as a JVM-wide singleton, not per-test-class `@Container`

`AbstractIntegrationTest` starts one `PostgreSQLContainer` in a static initializer and never stops
it, rather than using `@Testcontainers`/`@Container`. The container field is declared once on the
shared base class and inherited by every integration test class; letting the JUnit extension
manage its lifecycle stopped the (single, shared) container after the *first* test class's
`afterAll` and left every subsequent test class pointed at a dead container on a stale port
(`@DynamicPropertySource` had already captured the original mapped port). Starting it eagerly and
leaving it running for the whole JVM (Ryuk reaps it on shutdown) keeps the port stable for every
test class, and Spring's test context caching means most classes reuse the same
`ApplicationContext` too.

## 2026-07-18 — Unwrap the request with `WebUtils.getNativeRequest`, not a direct `instanceof`

`WebhookBodyCachingFilter` wraps the incoming request in `CachedBodyHttpServletRequest` so the
raw bytes are available twice: once for HMAC signature verification, once for JSON
deserialization. `CheckInWebhookController` originally checked
`request instanceof CachedBodyHttpServletRequest` directly, which always failed in the real
filter chain — Spring Security's `FilterChainProxy` re-wraps every request (e.g. in a
`FirewalledRequest`) before it reaches application code, so the object the controller receives is
never actually our filter's wrapper, even though the filter ran and the cached body exists further
down the wrapper chain. Fixed by using `WebUtils.getNativeRequest(request,
CachedBodyHttpServletRequest.class)`, which walks any chain of `ServletRequestWrapper`s to find
ours, regardless of how many other wrappers Spring has layered on top.

## 2026-07-18 — Personal records and weekly stats are computed on read, not cached

`StatsService` aggregates directly from `WorkoutSet`/`Workout` on every request rather than
maintaining a derived "current PR" or "this week's totals" row. This trades read-time aggregation
cost for eliminating an entire class of cache-invalidation race (a set write and a stats read
racing against a stale cached PR) — see `docs/CONCURRENCY.md` case 6. Acceptable at the scale of
one user's own workout history, backed by indexes on `workout_sets(workout_id, logged_at)` and
`workouts(user_id, status)`. Revisit with an explicit cache key + invalidation trigger if profiling
ever shows this is a bottleneck (tracked in `docs/FUTURE_ROADMAP.md`), rather than adding caching
speculatively.

## 2026-07-18 — Docker API version pinned for Testcontainers on Colima

Testcontainers 1.21.0 falls back to a hardcoded Docker Engine API version (1.32) when it can't
otherwise detect one, which a modern Docker Engine (e.g. Colima's bundled engine, minimum
supported API 1.40) rejects. `pom.xml` pins `-Dapi.version=1.41` (a conservative value that
satisfies that minimum while remaining supported by effectively any Docker Engine still in active
use) for the surefire/failsafe forked JVMs via the `docker.api.version` property. Running tests
against Colima also currently requires `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`
in the environment so the Ryuk sidecar container mounts the socket path as seen *inside* the
Colima VM rather than the host path in `DOCKER_HOST`.

## 2026-07-18 — Webhook uniqueness races recover in a new transaction

PostgreSQL aborts a transaction after a unique-constraint violation. Catching
`DataIntegrityViolationException` and re-querying in the *same* transaction therefore fails with
`current transaction is aborted` (surfaced as HTTP 500). `CheckInService` now runs the write path
in a `TransactionTemplate` and, on constraint races, looks up the winning `external_event_id` in a
fresh transaction (with brief retries) before returning `DUPLICATE` or `409`. Covered by
`ConcurrencyRaceIT`.

## 2026-07-18 — Coach bounds-first; LLM explains within bounds

The AI coach never mutates workouts. It only persists a `CoachRecommendation` in `PROPOSED`
status; the user must accept, modify, or dismiss. Numeric guardrails live in
`CoachBoundsCalculator` (no LLM dependency): ±10% weight, set removal capped at
`min(1, floor(20% of sets))`, deload only when 3+ declining sessions **and** soreness/stress ≥ 4,
and an injury-keyword short-circuit (`pain|hurt|injury|sharp`) that returns a fixed safety hold
without calling the generator. `CoachRecommendationGenerator` receives a `CoachContext` that
already includes those bounds; rule-based and OpenAI implementations only pick a value inside them
and write reasoning. `CoachAdviceNormalizer` clamps/rejects out-of-bounds or malformed model
output before persistence. Same provider switch as notes import (`gymsync.llm.provider`).

## 2026-07-18 — Exercise media is external-link-only

Exercises may carry optional `thumbnailUrl`, `demoVideoUrl`, and `mediaAttribution`. GymSync does
not upload, host, or scan media files — no object storage, CDN, or malware pipeline. Links point
at external reputable sources; URL format is validated locally (no live reachability check) so
create/update never depends on a third-party network call. `mediaAttribution` is required whenever
`demoVideoUrl` is set. Seeded thumbnails stay null unless a permissively licensed URL is confirmed
— we do not fabricate image links. The UI never iframes external video without an embedding-allowed
source; detail shows an outbound “Watch demonstration” link instead.

## 2026-07-18 — Program builder skips automated progression (v1)

Programs are user-owned planning data (days + planned exercises with target sets/reps/weight). This
version does not automate progressive overload, deload schedules, or auto-generated next-session
loads — those remain separate deterministic/coach features. Starting a program day creates a normal
`MANUAL`/`ACTIVE` workout (same one-active-workout 409 rules) and returns the day’s planned
exercises as guidance only; it never fabricates `WorkoutSet` rows. Programs hard-delete with
cascading days (unlike workouts, which soft-cancel because they are logged history).

## 2026-07-20 — Exercise media suggestions: embed + Commons, pending review

Auto-suggested exercise media never writes `thumbnailUrl` / `demoVideoUrl` onto `Exercise` until a
human confirms. Generate builds `exercise_media_suggestions` rows (`PENDING`) from: (1) a curated
map of common exercise names → reputable YouTube demo IDs (`CURATED`, high confidence); (2)
Wikimedia Commons search for File: namespace images with title attribution (“… via Wikimedia
Commons”); (3) optional YouTube Data API v3 search when `YOUTUBE_API_KEY` /
`gymsync.media.youtube-api-key` is set. The UI embeds YouTube via iframe only — GymSync does not
rehost video. We do not scrape brand/commercial fitness sites. This pending-review gate mirrors
notes-import honesty: unverified third-party lookups stay out of the catalog until confirm.
Confirm on custom exercises requires ownership; on globals, confirm is allowed only when published
media fields are still empty (first-publish), otherwise 409.

## 2026-07-20 — Batch 4: set type, per-side weight, and exercise notes storage

`WorkoutSet` gains `setType`, `rir`, `weightEntryMode`, `barWeight`, `enteredWeight`, `note`, and
`completed` (V8 migration). **`weight` remains the normalized total** used by all existing volume,
stats, and PR queries. **`enteredWeight` preserves what the user typed** for redisplay/edit:
`TOTAL` → `weight = enteredWeight`; `PER_SIDE` → `weight = barWeight + 2×enteredWeight`. Updates
always flow through `enteredWeight` + mode + `barWeight`, never direct `weight` edits when per-side
data exists. Exercise-level notes live in **`workout_exercise_notes`** (unique per workout +
exercise) — not on the first set's note field. Deferred: grip, stance, variation, unilateral reps,
equipment overrides.

## 2026-07-20 — Batch 4: active session prefill priority

When an exercise card is first shown: (1) applied coach recommendation for that exercise if present,
(2) program day planned target from sessionStorage, (3) last completed session for that exercise.
When adding another set in-session: (1) previous set in the current session, then the same fallback
chain. UI shows a small source label ("from coach", "from program", etc.).

## 2026-07-20 — Batch 4: rest timer state ownership

Rest timer state (`restEndsAt`, remaining seconds, +30s/skip) is owned by
`SessionRestTimerProvider` at the active-session page level — not inside exercise cards — so it
survives collapsing/expanding cards. In-memory only for this batch (no DB persistence). Auto-starts
after completing a working set; default duration varies by set type.

## 2026-07-20 — Batch 4: PR detection minimum criteria

PR evaluation runs only on **completed** sets with valid reps, excluding **WARMUP** and **DROP**
types. Comparison uses **normalized total `weight`**, never raw `enteredWeight`. Estimated-1RM PRs
are per-exercise only; sets with **reps > 12** may compute an estimate but **never trigger an e1RM
PR notice** (low formula confidence).

## 2026-07-20 — Batch 4: readiness → coach bounds translation

`CoachBoundsCalculator.classifyReadiness` maps existing check-in dimensions (sleep quality, energy,
soreness, stress — no muscle-specific soreness mapping) into **HIGH / MODERATE / LOW** tiers.
Structured recommendations include absolute `proposedWeight`, sets, rep range, and `targetRir`:
HIGH → small progression within ±10%; MODERATE → repeat last load / low rep range; LOW → conservative
load/volume reduction and higher target RIR. **LOW tier never proposes weight above last load**
(clamped in bounds). Pain/injury language unchanged (safety short-circuit). Applying a recommendation
requires explicit POST `/apply` after preview; only **uncompleted** sets for that exercise mutate.

## 2026-07-20 — Batch 4: deterministic 1RM + single coach tool

`Estimated1RmService` computes Epley, Brzycki, working average, and min–max range. Coach chat detects
1RM-style questions via `OneRmQuestionParser`, calls the same service, and phrases the response —
the LLM never performs the math. Manual calculator at `/strength` uses the same endpoint.
