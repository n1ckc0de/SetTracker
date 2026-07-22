# Concurrency and Transaction Design

Every race condition below is guarded primarily by a **database-level constraint or lock**, not
application-level (in-process) locking — in-process locks don't help once there's more than one
application instance, and every guarantee here needs to hold under multiple instances. Service-layer
pre-checks exist only to turn the common case into a clean, friendly error before the database
ever sees a conflicting write.

## 1. Two simultaneous attempts to create an active workout

**Mechanism:** unique partial index `idx_workouts_one_active_per_user` on
`workouts(user_id) WHERE status = 'ACTIVE'` (`V1__init_schema.sql`).

`WorkoutService.startWorkout` first checks for an existing `ACTIVE` workout and throws
`ConflictException` (409) as a fast path. If two requests both pass that check concurrently, both
attempt to insert; the database allows only one, and the loser's `DataIntegrityViolationException`
is caught and converted to the same 409. The same constraint also protects the webhook check-in
path (`CheckInService.process`) and applies uniformly regardless of how many application
instances are running.

**Trade-off:** the pre-check adds one extra `SELECT` per request but gives a clean error message
instead of surfacing a raw constraint-violation message.

**Tests:** `WorkoutControllerTest#startWorkout_whenAlreadyActive_returns409Conflict`,
`CheckInWebhookIntegrationTest#checkIn_userAlreadyHasActiveWorkout_returns409`,
`ConcurrencyRaceIT#concurrentStartWorkout_onlyOneActiveWorkoutIsCreated`.

## 2. Duplicate webhook deliveries

**Mechanism:** unique constraint `check_in_events_external_event_id_unique` on
`check_in_events.external_event_id`, checked first via a `SELECT`, then relied upon as the source
of truth if two deliveries race past that `SELECT` simultaneously.

`CheckInService.process` runs the write path inside a `TransactionTemplate`. On
`DataIntegrityViolationException` (duplicate event id **or** the one-active-workout partial
index), recovery happens in a **new** transaction: PostgreSQL aborts the failed transaction, so
re-querying inside it would raise `current transaction is aborted`. The recovery path briefly
retries looking up the winning `external_event_id`; if found, it returns `DUPLICATE`, otherwise
it surfaces a `409` active-workout conflict. **No code path can create two workouts for one
`eventId`.**

**Tests:** `CheckInWebhookIntegrationTest#checkIn_duplicateEventId_returnsDuplicateAndCreatesOnlyOneWorkout`,
`ConcurrencyRaceIT#concurrentDuplicateWebhook_createsOnlyOneWorkout`.

## 3. Concurrent completion of the same workout

**Mechanism:** optimistic locking (`@Version` on `Workout`).

`WorkoutService.completeWorkout` loads the workout, calls `workout.complete(...)` (which itself
guards against completing a non-`ACTIVE` workout), then `saveAndFlush`. If two requests race to
complete the same workout, the second `UPDATE` fails Hibernate's version check
(`ObjectOptimisticLockingFailureException`), which `GlobalExceptionHandler` maps to `409 Conflict`
("modified concurrently, please retry") rather than silently double-processing or throwing a 500.

**Trade-off:** optimistic locking (not pessimistic `SELECT ... FOR UPDATE`) was chosen because
workout completion is a low-contention, user-initiated action — a losing request retrying is
cheap and rare, so paying for a row lock on every read isn't justified.

**Tests:** `WorkoutControllerTest#completeWorkout_transitionsToCompletedWithEndedAt`,
`ConcurrencyRaceIT#concurrentCompleteWorkout_onlyOneSucceeds`.

## 4. Updating a set while its workout is being completed

**Mechanism:** `@Version` on both `Workout` and `WorkoutSet`, plus a same-transaction re-check of
workout mutability (`WorkoutSetService.assertMutable`) immediately before every set write.

A set can only be added/updated/deleted while its workout is `ACTIVE` or `PENDING_REVIEW`.
`assertMutable` re-reads the workout's current status inside the set-service's own transaction —
it does not trust a status a caller might have cached from an earlier response. If a
"complete workout" transaction commits between another request's mutability check and its set
write, the set write's own subsequent `saveAndFlush` either succeeds against a workout that is
still valid at that instant or the request is simply rejected with 409 on its own re-check; no
set can be attached to a workout that is not currently active/pending from the database's point
of view.

**Trade-off:** this does not need `@Version` cross-checked against the `Workout` row from the
`WorkoutSet` write path (no explicit `SELECT ... FOR UPDATE` on `Workout`), which means a set
write and a completion can still theoretically interleave such that the set is persisted a few
milliseconds before the completion commits — that set simply becomes part of the completed
workout's history, which is the intended, documented behavior (last set logged before completion
counts), not a bug.

**Tests:** `WorkoutSetControllerTest#addSet_onCompletedWorkout_returns409`.

## 5. Multiple note-import confirmation requests

**Mechanism:** optimistic locking (`@Version` on `Workout`), same pattern as case 3.

`NotesImportService.confirmImport`/`rejectImport` call `workout.confirmImport()` /
`workout.rejectImport()` — both throw `IllegalStateException` if `importStatus` is not already
`PENDING_REVIEW` — then `saveAndFlush`. Two concurrent confirm requests: one wins, the loser's
`saveAndFlush` throws `ObjectOptimisticLockingFailureException` → mapped to `409`. Confirm-then-
reject (or vice versa) races the same way: whichever transaction commits first "wins" the status
transition, and the second is rejected as a version conflict rather than allowed to silently
flip a workout from `CONFIRMED` back to `REJECTED` (or vice versa).

**Tests:** `NotesImportIntegrationTest#rejectImport_doesNotCountTowardStatistics`,
`ConcurrencyRaceIT#concurrentConfirmImport_onlyOneSucceeds`.

## 6. Simultaneous updates to personal-record data

**Design choice:** personal records and weekly statistics are **not stored/cached** — they are
computed on read (`StatsService`) directly from `WorkoutSet`/`Workout` rows via aggregation
queries. There is no derived/denormalized "current PR" row that two concurrent set-writes could
race to update inconsistently, which eliminates this class of race entirely rather than
mitigating it. The trade-off is recomputation cost on every read; this is acceptable at the scale
of a single user's own workout history and is backed by the indexes in spec §5
(`idx_workout_sets_workout_logged_at`, `idx_workouts_user_status`, etc.). If PR caching is added
later (spec §14 allows it), it must define an explicit cache key and invalidation trigger — see
`docs/FUTURE_ROADMAP.md`.

## 7. Duplicate/conflicting set numbers within a workout

Not explicitly listed in spec §19 but the same class of problem: unique constraint
`workout_sets_unique_set` on `(workout_id, exercise_id, set_number)`, pre-checked in
`WorkoutSetService` and caught as `DataIntegrityViolationException` → `409` on a race. See
`docs/DECISIONS.md` ("Unique set number per exercise per workout").

## Summary table

| Race condition | Primary mechanism | Fallback on race | HTTP result |
|---|---|---|---|
| Duplicate active workout | Unique partial index | Catch `DataIntegrityViolationException` | 409 |
| Duplicate webhook event | Unique constraint on `external_event_id` | Re-query and return existing result | 200 (duplicate) / 201 |
| Double-complete a workout | `@Version` + status guard | Catch `ObjectOptimisticLockingFailureException` | 409 |
| Set write vs. workout completion | Re-check mutability in same transaction | N/A (re-check is authoritative) | 409 if not mutable |
| Double confirm/reject import | `@Version` + status guard | Catch `ObjectOptimisticLockingFailureException` | 409 |
| Concurrent PR/stat reads | No cached derived state to corrupt | N/A | — |
| Duplicate set number | Unique constraint | Catch `DataIntegrityViolationException` | 409 |
