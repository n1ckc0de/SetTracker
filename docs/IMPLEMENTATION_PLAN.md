# GymSync API — Implementation Plan

**Status:** Phases 0–9 complete (2026-07-18). Post-phase batches 1–3 (readiness, coach, programs,
exercise media, SPA gap closure, CI frontend job) complete as of 2026-07-19 — see
[FULL_AUDIT_2026-07-19.md](FULL_AUDIT_2026-07-19.md) and
[BATCH3_COMPLETION_REPORT.md](BATCH3_COMPLETION_REPORT.md).  
**Base package:** `com.gymsync.api`  
**Stack:** Java 21, Spring Boot 3.5.x, Maven, PostgreSQL, Flyway, JWT, STOMP, Testcontainers, JaCoCo;
React/Vite SPA under `frontend/`

## Repository audit (Phase 0)

| Item | Finding |
|------|---------|
| Existing code | Full modular monolith under `src/main/java/com/gymsync/api/` |
| Spec | `PROJECT_SPEC.md` (authoritative for Phases 0–9) |
| Docs | §28 docs + §17/§18 designs; current product audit is `FULL_AUDIT_2026-07-19.md` |
| Tooling | Java 21 (Homebrew OpenJDK), Colima + Docker, Node for SPA |
| LLM | Optional OpenAI-compatible provider (e.g. Gemini); rule-based default without credentials |

### Decisions locked

- One active workout per user (partial unique index + service checks).
- Soft cancel on workout delete.
- Unique `(workout_id, exercise_id, set_number)`.
- Deterministic rule-based notes parser by default; optional OpenAI-compatible LLM.
- MIT license; author placeholders in README.

---

## Phase checklist

### Phase 0: Audit and planning
- [x] Inspect repository
- [x] Record current state
- [x] Create/update this implementation plan
- [x] Identify blockers
- [x] Establish baseline build

### Phase 1: Foundation
- [x] Spring Boot project + Maven wrapper
- [x] PostgreSQL / Docker Compose
- [x] Flyway migrations + seed exercises
- [x] Core domain entities + repositories
- [x] Base exception handling
- [x] Application properties / `.env.example`

### Phase 2: Authentication
- [x] Register / login endpoints
- [x] JWT security filter chain
- [x] Ownership helpers
- [x] Security tests

### Phase 3: Core REST API
- [x] Exercise CRUD (+ pagination/filter/sort)
- [x] Workout CRUD (+ complete/cancel)
- [x] Workout-set CRUD
- [x] Validation + OpenAPI
- [x] Controller & integration tests

### Phase 4: Webhook check-ins
- [x] HMAC signature + replay protection
- [x] Idempotent processing (incl. Postgres abort-safe race recovery)
- [x] Workout creation + audit record
- [x] Signing script + tests

### Phase 5: Live WebSocket sessions
- [x] STOMP `/ws/sessions`
- [x] JWT auth for subscriptions
- [x] Post-commit event publishing
- [x] Tests + docs

### Phase 6: AI notes import
- [x] `WorkoutNotesParser` interface
- [x] Deterministic local parser
- [x] Optional LLM implementation
- [x] Pending review / confirm / reject
- [x] Tests

### Phase 7: Statistics and recommendations
- [x] Personal records
- [x] Weekly statistics
- [x] Progressive-overload recommendations
- [x] Tests

### Phase 8: Hardening
- [x] Concurrency protections + `docs/CONCURRENCY.md`
- [x] Real multi-threaded race tests (`ConcurrencyRaceIT`)
- [x] Actuator (safe exposure)
- [x] Logging / correlation IDs
- [x] JaCoCo enforcement enabled
- [x] Full `./mvnw clean verify`

### Phase 9: GitHub readiness
- [x] README + all `docs/*`
- [x] CI workflow
- [x] API collection / scripts
- [x] License, templates, Dependabot
- [x] Compose app service enabled + Docker HEALTHCHECK fixed
- [x] Final audit report (`docs/FINAL_AUDIT.md`)

### Optional (after 0–9)
- [x] AI personal-trainer design (`docs/AI_PERSONAL_TRAINER.md`)
- [x] Equipment-photo workout generation design (`docs/EQUIPMENT_PHOTO_WORKOUTS.md`)

### Post-phase Batch 1–2 (backend + programs SPA)
- [x] Readiness check-ins API
- [x] Coach recommendations + chat API (bounds + injury short-circuit)
- [x] Stats volume-trend / category charts APIs
- [x] Exercise media columns + detail endpoints
- [x] Training programs API + frontend program builder

### Post-phase Batch 3 (gap closure — 2026-07-19)
- [x] Readiness + Coach SPA pages, Dashboard readiness card, nav entries
- [x] Exercise create/edit media fields with client-side attribution validation
- [x] `ReadinessControllerTest`, `CoachControllerTest`, `RuleBasedCoachRecommendationGeneratorTest`
- [x] Program controller/spec coverage gaps closed; `updateDay` flush-after-clear fix
- [x] CI `frontend-build` job (`npm ci` + `npm run build`)
- [x] Docs sync + live full-stack walkthrough (`BATCH3_COMPLETION_REPORT.md`)

---

## Progress log

| Date | Phase | Notes |
|------|-------|-------|
| 2026-07-18 | 0–9 | Prior session built core GymSync API end-to-end |
| 2026-07-18 | 0 | Re-audit vs DoD; closed gaps (Compose, HEALTHCHECK, JaCoCo, race tests, designs) |
| 2026-07-18 | 4/8 | Fixed `CheckInService` Postgres aborted-transaction recovery under concurrent webhook races |
| 2026-07-18 | 9 | `./mvnw clean verify` green (123 + 5 IT); Docker image builds; `FINAL_AUDIT.md` written |
| 2026-07-18 | Audit | Completion audit: added Spotless, SpotBugs, `docs/openapi.yaml` + `OpenApiSpecIT`, CI artifacts; `./mvnw clean verify` green (123 + 6 IT) |
| 2026-07-19 | Batch 1–2 | Readiness, coach, programs, media, stats charts, SPA programs |
| 2026-07-19 | Batch 3 | Frontend readiness/coach, CI frontend job, test gaps, docs, live E2E walkthrough |
