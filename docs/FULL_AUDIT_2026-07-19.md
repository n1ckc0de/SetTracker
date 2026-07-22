# GymSync — Full In-Depth Project Audit

**Date:** 2026-07-19
**Scope:** Entire repository as it stands today — backend, frontend, scripts, docs — against
`PROJECT_SPEC.md` §32 and every batch of work applied since the original Phases 0–9 build:
Batch 1 (readiness, coach, stats charts, exercise detail), Batch 2 (exercise media, program
builder), the frontend SPA build, and the `start-local.sh`/`stop-local.sh` dev scripts.
**Method:** Read-only. No code was changed in this pass. Every claim below is backed by a command
actually run in this session (`./mvnw clean verify`, `npm run build`, `docker build`, file reads,
greps) or a specific file/line citation — not inferred from prior audit docs alone.

---

## Executive summary

The **original Phases 0–9 core** (auth, exercise/workout/set CRUD, webhooks, WebSocket, notes
import, stats, concurrency, OpenAPI, CI, Docker) is in genuinely good shape: it compiles, the full
verification suite passes with real green numbers, and the three existing audit docs describing it
(`FINAL_AUDIT.md`, `COMPLETION_AUDIT.md`, `E2E_INTEGRATION_AUDIT.md`) hold up under re-verification.

**Everything added since those audits — the entire premise of this audit — is materially less
finished than the docs currently imply, because none of those docs mention it at all.** Concretely:

- **Readiness has a complete, tested backend and *zero* frontend surface.** There is no
  `/readiness` route, no page, no API client file, and no UI control anywhere in the SPA that lets
  a user create a readiness check-in. The only way to exercise `POST /api/readiness` today is
  Swagger or curl.
- **Coach is almost as bad.** Six backend endpoints exist and are well unit-tested at the
  service/domain layer, but the frontend only ever *displays* a recommendation if one already
  exists (`ExerciseDetailPage`, read-only) — there is no UI to generate, accept, modify, dismiss,
  or chat with the coach. A user can never actually produce a recommendation through the app.
- **The `CoachController` and `ReadinessController` HTTP layers have no dedicated tests at all**
  (0 test files; 10.5% and 22.2% instruction coverage respectively, both incidental).
- **Exercise media (thumbnail/demo video/attribution) is read-only in the UI.** The API client
  (`exercisesApi.ts`) supports passing media fields on create/update, but `ExercisesPage.tsx`'s
  actual create/edit forms never collect or send them — a user can view media if it exists, but
  cannot attach any through the app.
- **Program builder, by contrast, is fully and correctly wired end-to-end** — all 9 backend
  endpoints have a matching frontend call, and DTO shapes match exactly.
- **`docs/FINAL_AUDIT.md`, `docs/COMPLETION_AUDIT.md`, and `docs/IMPLEMENTATION_PLAN.md` all
  predate the readiness/coach/program work** (confirmed by file mtimes: all three were last
  written before `ReadinessController.java`, `CoachController.java`, and `ProgramController.java`
  existed) and **still describe "123 tests"** where the real current count is 158 unit + 6
  integration = 164. `docs/IMPLEMENTATION_PLAN.md` still says "Status: Complete — Phases 0–9" with
  no phase entry for any of this work. `README.md`'s Key Features, Domain Model, API Endpoint
  Summary, and Roadmap sections do not mention readiness, coach, programs, exercise media, or the
  frontend anywhere.
- CI (`.github/workflows/ci.yml`) only builds/tests the backend. There is no frontend job — a
  change that breaks `npm run build` or introduces a TypeScript error would pass CI today.

None of this is a regression or a broken build — `./mvnw clean verify`, `npm run build`, and
`docker build` all pass cleanly, and no source-code defect was found. The gap is entirely about
**incomplete frontend wiring for two of the four Batch 1 features, thin test coverage on the two
newest controllers, and documentation that has not caught up with two full batches of backend
work.** Call it roughly 80% done relative to what "readiness/coach/stats/exercise-detail +
exercise-media/program-builder, fully wired and documented" would mean — solid backend, half-wired
frontend, stale docs.

---

## 1–2. Inventory

### Backend packages (`src/main/java/com/gymsync/api/*`)

| Package | Contents | Status |
|---|---|---|
| `auth` | controller/dto/security/service — register, login, JWT filter chain, ownership | COMPLETE (pre-existing, re-verified) |
| `checkin` | HMAC webhook controller/service/validator, body-caching filter, idempotency | COMPLETE (pre-existing, re-verified) |
| `common` | exceptions, CORS/Jackson/Actuator config, correlation-id filter, `TransactionUtils` | COMPLETE (pre-existing, re-verified) |
| `exercise` | controller/service/domain/dto — CRUD, detail endpoint, media validator, name normalizer | COMPLETE, see §3 for the media-UI gap |
| `notesimport` | rule-based + LLM parser, pending-review workflow | COMPLETE (pre-existing, re-verified) |
| `stats` | weekly stats, PR, PR history, volume trend, overload recommendation | COMPLETE |
| `user` | domain/repository only (no dedicated service/controller — used by other domains) | COMPLETE (thin by design) |
| `websocket` | STOMP config, auth interceptor, session event publisher | COMPLETE (pre-existing, re-verified) |
| `workout` | controller/service/domain/dto — workout + set CRUD, completion, soft-cancel | COMPLETE (pre-existing, re-verified) |
| `readiness` | controller (3 endpoints), service (well-tested), domain, repository, dto | **Backend COMPLETE, frontend MISSING** — see §3 |
| `coach` | controller (6 endpoints), bounds calculator, rule-based + LLM generators, service, domain, repository | **Backend COMPLETE, frontend PARTIAL (read-only)** — see §3 |
| `program` | controller (9 endpoints), service, domain (Program/ProgramDay/ProgramDayExercise), repository | COMPLETE, frontend fully wired |

### Frontend routes (`frontend/src/App.tsx`)

| Route | Page | Wired to real API? |
|---|---|---|
| `/` | `DashboardPage` | Yes — workouts, PRs |
| `/workouts`, `/workouts/:id` | `WorkoutsPage`, `WorkoutDetailPage` | Yes |
| `/session` | `ActiveSessionPage` | Yes, incl. live STOMP updates |
| `/exercises`, `/exercises/:id` | `ExercisesPage`, `ExerciseDetailPage` | Yes — but see media-form gap in §3 |
| `/programs`, `/programs/:id` | `ProgramsPage`, `ProgramDetailPage` | Yes, all 9 program endpoints |
| `/import` | `ImportNotesPage` | Yes |
| `/stats` | `StatsPage` | Yes — volume trend chart, weekly stats, PRs |
| *(none)* | — | **No route, page, or API client exists for `/api/readiness/*`** |
| *(none)* | — | **No route or page for generating/managing coach recommendations** — `CoachRecommendation` is only ever read, inside `ExerciseDetailPage` |

Confirmed by exhaustive grep (`grep -rli "sleepHours|sleepQuality|energyLevel|soreness|stress"
frontend/src/` → zero matches) that readiness has no UI presence under any naming.

### DoD checklist

**PROJECT_SPEC.md §32 (original, pre-batch scope)** — re-verified in this session, all still hold:

| Item | Status | Evidence |
|---|---|---|
| Compiles / migrations / Docker local dev | COMPLETE | `./mvnw clean verify` BUILD SUCCESS; `docker build` SUCCESS this session |
| Registration, login, JWT, ownership | COMPLETE | `AuthControllerTest` (9), `JwtServiceTest` (4), cross-user 404 tests throughout |
| Exercise/Workout/Set CRUD + completion | COMPLETE | Controller test suites green |
| Webhook validation + duplicate-safe | COMPLETE | `WebhookSignatureValidatorTest` (9), `CheckInWebhookIntegrationTest` (7), `ConcurrencyRaceIT` |
| WebSocket after-commit events | COMPLETE | `TransactionUtilsTest`, `StompSessionEventPublisherTest` |
| Notes import (rule-based default, pluggable LLM, review workflow) | COMPLETE | `NotesImportIntegrationTest`, `RuleBasedWorkoutNotesParserTest`, `LlmWorkoutNotesParserTest` |
| PRs, weekly stats, overload recommendations | COMPLETE | `StatsServiceTest`, `DeterministicOverloadRecommendationEngineTest` |
| Consistent validation errors, cross-user tests, concurrency protections | COMPLETE | `GlobalExceptionHandler`, `ConcurrencyRaceIT` (4 tests) |
| OpenAPI docs match live spec | COMPLETE | `OpenApiSpecIT` passed — see §4 |
| Actuator health safe | COMPLETE | public health only, verified in `SecurityConfig` |
| Unit + integration tests pass, `./mvnw clean verify` passes | COMPLETE | 158 + 6 = 164 tests, 0 failures |
| Docker image builds | COMPLETE | rebuilt in this session, exit 0 |
| GitHub Actions present and coherent | **PARTIAL** | present and coherent for backend; **no frontend job** — see §4/§5 |
| No secrets committed | COMPLETE | `.env.example` only, `.gitignore` covers `.env` |
| README reflects implementation status | **MISSING** (for current state) | accurate for pre-batch scope; silent on readiness/coach/programs/exercise-media/frontend — see §6 |
| Roadmap labels unfinished features | PARTIAL | correctly labels the *original* stretch goals (AI trainer, equipment-photo) as deferred; does not mention that readiness/coach frontend wiring is incomplete, because it doesn't mention those features existing at all |

**Batch 1 — readiness, coach, stats charts, exercise detail:**

| Item | Status | Reason |
|---|---|---|
| Readiness check-in backend (create/latest/list) | COMPLETE | `ReadinessService` + `ReadinessServiceTest` (5 tests, 89.8%/100% coverage) |
| Readiness check-in frontend | **MISSING** | No route, page, API client, or any UI control. Confirmed by exhaustive grep. |
| Readiness HTTP-layer tests | **MISSING** | No `ReadinessControllerTest`; controller sits at 22.2% instruction / 50% line coverage, both incidental |
| Coach recommendation backend (generate/list/accept/modify/dismiss/chat) | COMPLETE | `CoachService`, `CoachBoundsCalculator` (97.3% cov.), `CoachAdviceNormalizer` all tested |
| Coach recommendation frontend | **PARTIAL** | Read-only display of an existing recommendation inside `ExerciseDetailPage`; no way to generate, accept, modify, dismiss, or chat |
| Coach HTTP-layer tests | **MISSING** | No `CoachControllerTest`; controller sits at 10.5% instruction / 30% line coverage |
| Rule-based (default, no-LLM) coach generator tests | **MISSING** | No `RuleBasedCoachRecommendationGeneratorTest`; zero references to the class anywhere under `src/test` — the actual default code path is untested in isolation |
| Stats charts (volume trend, PR history) | COMPLETE | `StatsPage.tsx` renders real `recharts` charts from `statsApi.volumeTrend`/`prHistory`; DTOs match exactly |
| Exercise detail page/endpoint | COMPLETE | `ExerciseDetailPage.tsx` ↔ `GET /api/exercises/{id}/detail`, all fields match `ExerciseDetailResponse` |

**Batch 2 — exercise media, program builder:**

| Item | Status | Reason |
|---|---|---|
| Exercise media backend (validation, storage, DTOs) | COMPLETE | `ExerciseMediaValidatorTest` (4 tests), `ProgramControllerTest#mediaValidation_onExerciseCreate` |
| Exercise media read/display frontend | COMPLETE | Thumbnail rendering in `ExercisesPage.tsx`, demo-video link + attribution in `ExerciseDetailPage.tsx` |
| Exercise media write frontend (create/edit forms) | **MISSING** | `exercisesApi.createExercise`/`updateExercise` accept an optional `media` argument; `ExercisesPage.tsx`'s actual forms (`onCreate`, `onSaveEdit`) never pass it — no input fields exist for `thumbnailUrl`/`demoVideoUrl`/`mediaAttribution` |
| Program builder backend (CRUD, days, start-day) | COMPLETE | 9 endpoints, `ProgramControllerTest` covers most of the surface — see §3 for the untested subset |
| Program builder frontend | COMPLETE | All 9 endpoints called from `programsApi.ts`, wired into `ProgramsPage.tsx`/`ProgramDetailPage.tsx`, types match exactly |

**Dev tooling (this session's prior work):**

| Item | Status | Reason |
|---|---|---|
| `scripts/start-local.sh` / `scripts/stop-local.sh` | COMPLETE | Actually executed and verified multiple times in the prior session (clean start, restart-while-running, full teardown, port verification) |

---

## 2. Partial-completion details

Specifics for every item marked PARTIAL above, per the standard requested ("mostly works" is not
an answer):

1. **Coach recommendation frontend (`ExerciseDetailPage.tsx:159-172`).** The page reads
   `detail.activeRecommendation` from `GET /api/exercises/{id}/detail` and renders it if non-null.
   `ExerciseDetailService.java` populates that field from `CoachRecommendationRepository` — but
   nothing in the frontend ever calls `POST /api/coach/recommendations/generate` or
   `POST /api/coach/chat`, so in real usage this field is null for every user unless a
   recommendation was created out-of-band via Swagger/curl. The accept/modify/dismiss PATCH
   endpoints (`/api/coach/recommendations/{id}/accept|modify|dismiss`) have no frontend caller at
   all — confirmed by grep across `frontend/src/api/*.ts` (no `coachApi.ts` file exists).

2. **Exercise media on create/edit (`frontend/src/pages/ExercisesPage.tsx`).**
   `exercisesApi.createExercise(name, category, media?)` and `updateExercise(id, name, category,
   media?)` both accept an optional third argument for `thumbnailUrl`/`demoVideoUrl`/
   `mediaAttribution` (`exercisesApi.ts:30-71`). The page's actual submit handlers call
   `exercisesApi.createExercise(name.trim(), newCategory)` (line 93) and
   `exercisesApi.updateExercise(editing.id, editing.name, editing.category)` (line 111) — no third
   argument, and the form's `useState` hooks (`name`, `newCategory`, `editing`) never track media
   fields, so there is no input for them in the JSX either. A user can only ever attach media to a
   custom exercise via direct API access.

3. **`ProgramController` test coverage (39.2% instruction / 42.9% line).**
   `ProgramControllerTest` has 2 test methods covering: create, get, delete (404-only, other-user),
   update (404-only, other-user — the owner happy-path for `PUT /api/programs/{id}` is never
   exercised), `addDay` (success + 409 conflict), `startDay` (success + 409 conflict), and a
   separate exercise-media validation test. **Not covered by any test:** `GET /api/programs` (list),
   `PUT /api/programs/{id}/days/{dayNumber}` (update day), `DELETE /api/programs/{id}/days/{dayNumber}`
   (delete day), and the owner-success path of `PUT /api/programs/{id}`.

4. **Roadmap in `README.md`.** Correctly marks the *original* stretch goals (AI personal trainer,
   equipment-photo workouts) as deferred/design-only. It does not mention readiness, coach, or
   programs at all, so a reader has no way to learn from the roadmap that those exist, let alone
   that their frontend wiring is incomplete.

5. **GitHub Actions coherent but backend-only.** `.github/workflows/ci.yml` sets up Java, caches
   Maven, runs `./mvnw -B clean verify`, and uploads JaCoCo/artifacts. There is no `node`/`npm`
   step anywhere in the file (confirmed by grep). `npm run build` (which includes a `tsc -b`
   typecheck) is never run in CI — a broken frontend build would merge cleanly.

---

## 3. Manual / credential-dependent items

Things that cannot be verified by an automated run in this environment:

- **Live OpenAI-compatible LLM calls** (`gymsync.llm.provider=openai` + `LLM_API_KEY`), used by
  both `LlmWorkoutNotesParser` and `LlmCoachRecommendationGenerator`. `.env.example`'s
  `LLM_API_KEY=` is blank; no key is configured in this environment. What **is** verified: the
  rule-based defaults (`RuleBasedWorkoutNotesParserTest`, 9 tests) for notes import, and the LLM
  client's request/response *handling* against a mocked HTTP client (`LlmWorkoutNotesParserTest`,
  14 tests; `LlmCoachRecommendationGeneratorTest`, 3 tests) — i.e., parsing/validation of a
  simulated response, not a real round-trip to OpenAI. This matches what the prior audits already
  stated and remains true; it is not a new gap.
- **Production JWT/webhook secrets** — only dev placeholders exist in `.env.example`, as intended.
- **`RuleBasedCoachRecommendationGenerator` has no direct test coverage** (see §1). This is
  *not* credential-dependent — it needs no external service — so it should not be left
  unverified; it's called out here because it's currently in the same "unverified" bucket as the
  genuinely credential-gated LLM path, and it shouldn't be.
- **Prior "unverified" items from `docs/FINAL_AUDIT.md`/`docs/E2E_INTEGRATION_AUDIT.md`** (live
  OpenAI path, production secrets) remain unresolved in the same way — nothing has changed there,
  and nothing in this session's scope required changing it.
- **Manual UX click-through of the frontend** was not performed as part of this read-only audit
  (see §7 — this is explicitly a task for a human, not something to infer from source review).

---

## 4. Consistency findings

**Frontend TypeScript types vs backend DTOs** — sampled one endpoint group per batch:

| Domain | Result |
|---|---|
| Exercise (incl. media fields) | **No drift.** `Exercise` (types.ts:53-64) matches `ExerciseResponse.java` field-for-field. |
| Program builder (`ProgramSummary`, `ProgramDetail`, `ProgramDay`, `PlannedExercise`, `StartProgramDayResponse`) | **No drift.** Every field in `types.ts:188-227` matches `ProgramSummaryResponse`/`ProgramDetailResponse`/`StartProgramDayResponse.java` exactly. |
| Coach (`CoachRecommendation` type) | **No drift.** `types.ts:158-171` matches `CoachRecommendationResponse.java` exactly — the type is correct, it's simply almost never populated because of the frontend-wiring gap in §2. |
| Stats (`WeeklyStats`, `PersonalRecord`, `VolumeTrend`, `PrHistory`, `NextSessionRecommendation`) | **No drift.** All five sampled types match their `*.dto` counterparts field-for-field, including nested `optionA`/`optionB` and `comparisonWithPriorWeek` shapes. |

No type drift was found anywhere sampled. The gap in this codebase is not "wrong types," it's
"types that exist correctly but are never called" (readiness has no client at all; coach's client
functions for generate/accept/modify/dismiss/chat don't exist).

**OpenAPI drift** — ran the existing check rather than assuming: `OpenApiSpecIT` (part of
`./mvnw clean verify`, Failsafe suite) does a byte-for-byte comparison of `GET /v3/api-docs.yaml`
against the committed `docs/openapi.yaml` and **passed** (`Tests run: 1, Failures: 0` at
`OpenApiSpecIT`, log line 241 of this session's verify run). **`docs/openapi.yaml` is in sync with
the live spec, including readiness/coach/program endpoints** — this part of the toolchain is
working exactly as designed and caught nothing to flag.

**CORS / env / compose / vite ports** — all four locations agree:

| Location | Value |
|---|---|
| `application.yml` (`gymsync.cors.allowed-origins` default) | `http://localhost:3000,http://localhost:5173,http://localhost:8080` |
| `.env.example` (`CORS_ALLOWED_ORIGINS`) | same |
| `compose.yaml` (`app.environment.CORS_ALLOWED_ORIGINS`) | same |
| `frontend/vite.config.ts` | dev server port `5173`, proxies `/api` and `/ws` to `localhost:8080` |
| `scripts/lib/dev-common.sh` | frees `8080`, `5432`, `5173`, `5174` before every start |

No drift. One minor observation, not a defect: if Vite falls back to port `5174` (its default
behavior when `5173` is occupied), that origin is not in the CORS allow-list — but
`start-local.sh` exists specifically to free `5173` first, so this shouldn't occur through normal
use of the documented startup path.

---

## 5. Test suite results

**`./mvnw clean verify`** — run in full in this session, real output:

```
Surefire (unit + MockMvc):  Tests run: 158, Failures: 0, Errors: 0, Skipped: 0
Failsafe (Testcontainers):  Tests run: 6,   Failures: 0, Errors: 0, Skipped: 0
  - OpenApiSpecIT            1 test
  - EndToEndWorkoutFlowIT    1 test
  - ConcurrencyRaceIT        4 tests
JaCoCo:      "All coverage checks have been met." (149 classes analyzed)
Spotless:    181 Java files clean, 2 other files clean
SpotBugs:    0 bugs, 0 errors
Result:      BUILD SUCCESS (27.9s)
```

Total: **164 tests, 100% passing, 0 flakes.** This is higher than every number in the existing
audit docs (`123` unit + `5`–`6` IT), consistent with those docs predating the readiness/coach/
program work.

**`npm run build`** — run in full, real output:

```
tsc -b && vite build
✓ 612 modules transformed
dist/index.html                 0.80 kB
dist/assets/index.css            7.26 kB (gzip 2.09 kB)
dist/assets/index.js           681.74 kB (gzip 198.13 kB)
✓ built in 186ms
```

Succeeds, no TypeScript errors. One build-time warning worth flagging: the single JS chunk is
681.74 kB (198 kB gzipped), over Vite's 500 kB default warning threshold — not a failure, but
there is no code-splitting (no `React.lazy`/dynamic `import()` anywhere in `App.tsx`'s routes), so
every route's code ships on first load.

**`docker build`** — rebuilt in this session (`docker build -t gymsync-api:audit .`): succeeded,
multi-stage build, exit 0.

**JaCoCo coverage by package** (instruction % / line %, computed from `target/site/jacoco/jacoco.csv`
generated by this session's `verify` run), weakest first:

| Package | Instr. | Line | Note |
|---|---|---|---|
| `program.repository` | 0.0% | 0.0% | `ProgramSpecifications` predicate lambda never directly exercised by a test (1 executable line) |
| `coach.controller` | 10.5% | 30.0% | No `CoachControllerTest` — see §2 |
| `readiness.controller` | 22.2% | 50.0% | No `ReadinessControllerTest` — see §2 |
| `checkin.web` | 38.1% | 42.1% | Pre-existing, not part of this audit's batches |
| `program.controller` | 39.2% | 42.9% | Partial — see §2 item 3 |
| `coach.llm` | 46.5% | 43.8% | `RuleBasedCoachRecommendationGenerator` has no direct test — see §1/§3 |

By contrast, the packages backing the well-tested Batch 1/2 *service*-layer logic are strong:
`coach.bounds` 97.3%/97.5%, `coach.domain` 95.2%/92.1%, `readiness.service` 89.8%/100%,
`readiness.domain` 90.5%/90.0%, `program.domain` 81.7%/78.1%, `program.service` 56.0%/62.6%. The
pattern is consistent: **domain/service logic for the new features is well tested; the HTTP
controller layer for coach and readiness specifically is not.** This is the same shape of gap as
§1/§2, not a separate issue.

---

## 6. Documentation accuracy findings

**`docs/FINAL_AUDIT.md`, `docs/COMPLETION_AUDIT.md`, `docs/IMPLEMENTATION_PLAN.md`** — confirmed
stale by file mtime, not just content inspection:

| File | Last written | Predates |
|---|---|---|
| `docs/FINAL_AUDIT.md` | 2026-07-18 12:35 | `ReadinessController.java` (20:18), `CoachController.java` (20:25), `ProgramController.java` (21:14) |
| `docs/COMPLETION_AUDIT.md` | 2026-07-18 12:50 | same |
| `docs/IMPLEMENTATION_PLAN.md` | 2026-07-18 12:50 | same |
| `docs/E2E_INTEGRATION_AUDIT.md` | 2026-07-18 20:00 | `ReadinessController.java` (20:18), `CoachController.java` (20:25) — created 18 minutes before readiness landed |

None of the three earlier audits' package-boundary lists (`auth, exercise, workout, checkin,
websocket, notesimport, stats, common`) include `readiness`, `coach`, or `program` — they describe
a real, correctly-audited state, but it is the *pre-batch* state.
`docs/IMPLEMENTATION_PLAN.md:3` still reads **"Status: Complete — Phases 0–9"** with a phase
checklist that stops at Phase 9 plus the two optional design docs; there is no Phase entry, however
labeled, for readiness, coach, programs, exercise media, or the frontend build itself.

**`docs/DECISIONS.md` — the exception, and it's accurate.** Unlike the three audit docs, this one
*was* updated through Batch 2: it has dated entries for "Coach bounds-first; LLM explains within
bounds" (2026-07-18), "Exercise media is external-link-only," and "Program builder skips automated
progression (v1)," all of which accurately describe the current implementation. It does not have
an entry for the frontend architecture or for the dev scripts, but those are arguably tooling
rather than the kind of design decision this log is for.

**`README.md`** — Local Installation section is accurate and current (it documents
`./scripts/start-local.sh`/`stop-local.sh`, added in the prior session). Everywhere else, it
describes only the pre-batch feature set:

- **Key Features** (lines 20–39): lists auth, exercise catalog, workout tracking, webhooks,
  WebSocket, notes import, stats/recommendations. No mention of readiness, coach, or programs.
- **Domain Model Summary** (lines 86–97): lists `User`, `Exercise`, `Workout`, `WorkoutSet`,
  `CheckInEvent`. No `ReadinessCheckIn`, `CoachRecommendation`, `Program`/`ProgramDay`/
  `ProgramDayExercise`.
- **API Endpoint Summary** (lines 99–119): lists the original ~14 routes. No `/api/readiness/*`,
  `/api/coach/*`, `/api/programs/*`, `/api/exercises/{id}/detail`, `/api/exercises/{id}/pr-history`,
  or `/api/stats/volume-trend`.
- **Roadmap** (lines 375–389): "Completed" list matches the original feature set only.

`frontend/README.md` is accurate for what it covers (setup, CORS, auth storage, scripts) but its
"End-to-end smoke path" (6 steps) doesn't mention Programs or Exercise Detail, and — consistent
with the actual gap found in §1/§2 — appropriately doesn't claim readiness or coach exist either.

---

## 7. What Nicholas should personally do next

Things in this repo that need human judgment or a manual action, not more agent-written code:

1. **Decide whether readiness/coach need frontend UI now, or are staying backend-only for a
   while.** This audit found the gap; closing it (a readiness check-in form, a way to generate/
   respond to coach recommendations) is a real scope decision about what the app is *for* right
   now, not a mechanical fix — worth deciding deliberately rather than having an agent guess at a
   UI design.
2. **Review `CoachBoundsCalculator` yourself.** It's the one piece of this codebase where a bug
   has a different character than a normal defect — it's the code that caps how much weight/volume
   change the "AI" is allowed to ever propose, and it's currently only guarded by unit tests
   (well-covered ones, 97%+, but still only tests). Read it and satisfy yourself the bounds (±10%
   weight, set-removal cap, deload trigger threshold, injury-keyword short-circuit) are actually
   the limits you want in place before this is used for real training decisions, however casually.
3. **If you want the LLM-backed paths verified for real**, obtain an `LLM_API_KEY` and run the app
   with `LLM_PROVIDER=openai` against both `notesimport` and `coach` at least once. Nothing in this
   codebase currently exercises a real OpenAI round-trip; both mocked-client test suites pass, but
   that's a different claim.
4. **Manually click through the frontend** — register, log a workout, import notes, browse a
   program, look at stats — specifically watching for UX rough edges (the 682 kB single JS bundle
   noted in §5, empty states, error messages) that no automated test in this repo would catch.
   This audit was read-only/source-level for the frontend beyond the build step; it did not drive
   the app in a browser.
5. **Decide on the deferred stretch features** (equipment-photo workouts, the broader "AI personal
   trainer" beyond the bounds-checked coach, a multi-instance WebSocket broker) — these are
   correctly labeled as design-only/deferred in the README and have design docs already
   (`docs/EQUIPMENT_PHOTO_WORKOUTS.md`, `docs/AI_PERSONAL_TRAINER.md`); whether to ever build them
   is a product decision, not something this audit can resolve.
6. **When you do close the readiness/coach frontend gap (or decide not to)**, update
   `docs/IMPLEMENTATION_PLAN.md`, `docs/FINAL_AUDIT.md`/`docs/COMPLETION_AUDIT.md` (or retire them
   in favor of this one), and `README.md`'s Key Features/Domain Model/API Endpoint Summary/Roadmap
   sections in the same pass — the docs drifted this far behind once already; it's worth deciding
   who/what is responsible for keeping them current going forward (e.g., requiring a docs update
   as part of each batch's own definition of done).
