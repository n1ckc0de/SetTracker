# GymSync — Batch 3 Completion Report

**Date:** 2026-07-19 (session)  
**Audit closed:** [FULL_AUDIT_2026-07-19.md](FULL_AUDIT_2026-07-19.md)  
**Evidence log:** `logs/batch3-e2e-evidence.txt`

## Summary

Batch 3 closed every frontend, test, CI, and documentation gap called out in the 2026-07-19 full audit. The live end-to-end journey (register → readiness → media exercise → program day start → sets → coach generate/accept → injury chat → notes import → stats → SPA nav) was executed against a running local stack with Gemini (`LLM_PROVIDER=openai`, `LLM_MODEL=gemini-3.5-flash`) and succeeded.

## What was built

### 1. Readiness frontend + tests
- Route `/readiness` (`ReadinessPage.tsx`) with sleep/energy/soreness/stress inputs and optional note
- `frontend/src/api/readinessApi.ts`
- Latest check-in card on Dashboard; “Readiness” sidebar entry
- `ReadinessControllerTest` — create/latest/list, validation (1–5 bounds), auth, ownership

### 2. Coach frontend + tests
- Route `/coach` (`CoachPage.tsx`): generate, list, accept/modify/dismiss, chat
- `frontend/src/api/coachApi.ts` for all six endpoints
- Exercise detail “generate recommendation” action
- “Coach” sidebar entry (harbor/mist/sage/sand tokens only)
- `CoachControllerTest` — full HTTP coverage including injury short-circuit (`CoachRecommendationGenerator` never called)
- `RuleBasedCoachRecommendationGeneratorTest` — isolated default-path behavior and bound edges

### 3. Exercise media form
- `thumbnailUrl`, `demoVideoUrl`, `mediaAttribution` on create/edit in `ExercisesPage.tsx`
- Client-side rule: attribution required when demo URL is set

### 4. CI frontend job
- `.github/workflows/ci.yml` job `frontend-build`: Node 22, `npm ci`, `npm run build` in `frontend/`
- Same least-privilege `contents: read` and pinned action versions as the API job

### 5. Program / specs test gaps
- `ProgramControllerTest` — list, update day, delete day, owner PUT program
- `ProgramSpecificationsTest` — `forUser` predicate
- **Bugfix:** `ProgramService.updateDay` now `saveAndFlush` after `clearExercises()` so unique `(program_day_id, order_in_day)` does not collide on replace

### 6. Documentation
- `README.md` — features, domain, API summary, structure, roadmap, limitations
- `docs/IMPLEMENTATION_PLAN.md` — Batches 1–3 status
- `docs/FINAL_AUDIT.md` / `docs/COMPLETION_AUDIT.md` — superseded pointer to full audit + this report
- `frontend/README.md` — expanded smoke path

### Supporting change (LLM observability)
- INFO logs in `LlmCoachRecommendationGenerator` and `LlmWorkoutNotesParser` for outbound call + response status (no secrets), so live Gemini hits are visible in `logs/api.log`

## Verification in this session

| Check | Result |
|-------|--------|
| `./mvnw clean verify` | **PASS** |
| `npm run build` (frontend) | **PASS** |
| Live E2E walkthrough | **PASS** (see below) |

### Walkthrough evidence (2026-07-20 UTC)

Stack: `scripts`/manual Postgres + `spring-boot:run` profile `local` + Vite on `:5173`. Full request/response bodies are in `logs/batch3-e2e-evidence.txt`.

| Step | Result |
|------|--------|
| 1. Register → login | HTTP 201 / 200, JWT issued |
| 2. Readiness → latest | HTTP 201 / 200; note “Feeling good for Batch3” on latest |
| 3. Custom exercise + media | HTTP 201 with thumbnail, demo URL, attribution |
| 4. Program + start day | `plannedExercises=1`, `fabricatedSets=0` |
| 5. Log sets → complete | Two sets logged; workout `COMPLETED` |
| 6. Coach generate → accept | HTTP 201/200; Gemini called (`status=200`); status `ACCEPTED` |
| 7. Injury chat | `SAFETY_HOLD` fixed reasoning; **0** “Calling LLM provider for coach” log lines after chat |
| 8. Notes import → confirm | Gemini called (`status=200`); `importStatus=CONFIRMED` |
| 9. Stats | Weekly `totalVolume=2240`, `volumeByCategory` CHEST+OTHER; trend current week 2240 |
| 10. Sidebar routes | All of `/`, `/workouts`, `/session`, `/exercises`, `/programs`, `/readiness`, `/coach`, `/import`, `/stats` → HTTP **200** |

#### Gemini confirmation (not rule-based)

From `logs/api.log` during the walkthrough:

```text
Calling LLM provider for coach recommendation. url=https://generativelanguage.googleapis.com/v1beta/openai/chat/completions model=gemini-3.5-flash attempt=1/2
LLM provider responded for coach recommendation. ... status=200

Calling LLM provider for notes import. url=https://generativelanguage.googleapis.com/v1beta/openai/chat/completions model=gemini-3.5-flash attempt=1/2
LLM provider responded for notes import. ... status=200
```

Injury chat produced no coach LLM “Calling…” lines after the request mark.

## Remaining known limitations

- In-memory STOMP broker is single-instance only.
- Stats are computed on read (no cache).
- Coach accept/modify records intent only; it does not mutate future workouts.
- Notes-import LLM may create ad-hoc exercise names/categories (`OTHER`) when catalog match is weak — expected for ambiguous free text; still validated and human-confirmed before stats.
- Local API processes must stay attached to a live shell (or equivalent process supervision); `nohup` alone can be reaped when the parent Cursor shell exits. Prefer `./scripts/start-local.sh` from a durable terminal.
- Duplicate `* 2.class` artifacts under `target/` (macOS Finder copies) can break `spring-boot:run` main-class detection — `./mvnw clean` clears them.

## Definition of done checklist

- [x] Sections 1–6 implemented; their tests pass under `./mvnw clean verify`
- [x] Section 7 walkthrough run in this session with evidence
- [x] `./mvnw clean verify` and `npm run build` clean
- [x] No sidebar item maps to a missing page (all return 200)
- [x] Docs describe only what was verified here
- [x] This report written
