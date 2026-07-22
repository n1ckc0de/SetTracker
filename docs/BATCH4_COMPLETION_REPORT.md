# GymSync Batch 4 — Completion Report

**Date:** 2026-07-20  
**Scope:** Active session upgrade, estimated 1RM, readiness-aware coaching, dashboard "Today" redesign.

## Verification summary

| Check | Result | Evidence |
|-------|--------|----------|
| `./mvnw clean verify` | **PASS** | 201 unit + integration tests, SpotBugs clean |
| `npm run build` | **PASS** | TypeScript + Vite production build |
| `npm test` | **PASS** | 9 frontend tests (batch sets, prefill, rest timer) |
| Flyway V8 | **Applied** | `workout_sets` extensions + `workout_exercise_notes` |
| OpenAPI | **Updated** | `docs/openapi.yaml` regenerated |

## §1 WorkoutSet extension + exercise notes

- **Migration:** `V8__workout_set_extensions_and_exercise_notes.sql`
- **Enums:** `SetType`, `WeightEntryMode`
- **Normalization:** `SetWeightNormalizer` — `weight` = total load; `enteredWeight` preserves user input
- **Exercise notes:** `WorkoutExerciseNote` entity + `workout_exercise_notes` table (no `WorkoutExercise` existed)
- **API:** batch sets, complete set, exercise note CRUD; DTOs expose all new fields

**Tests:** `SetWeightNormalizerTest`, extended `WorkoutSetControllerTest` (per-side round-trip, batch, complete)

## §2 Active session UI

- Exercise cards grouped by exercise with warm-up/working badges
- Set row: # | Type | Weight | Reps | RIR | Complete
- Batch add, prefill with source labels, total/per-side toggle, set + exercise notes
- **Rest timer:** `SessionRestTimerProvider` at page level (survives card navigation)
- PR toasts from `prEvent` on complete

**Tests:** `activeSession.test.ts`, `sessionRestTimer.test.tsx`

## §3 Estimated 1RM

- **`Estimated1RmService`:** Epley, Brzycki, working average, range, low-confidence flag (reps > 12)
- **REST:** `POST /api/strength/estimate-1rm`
- **Manual UI:** `/strength` nav item + link from Exercise Detail
- **Coach tool:** `OneRmQuestionParser` → deterministic service → phrased response (no LLM math)

**Tests:** `Estimated1RmServiceTest`, `CoachControllerTest` — manual calculator and coach chat return **identical `workingEstimate`** for 225×5

## §3.5 Readiness-aware coach + apply

- **`CoachBoundsCalculator.ReadinessTier`:** HIGH / MODERATE / LOW from existing check-in fields
- **Structured `proposedChange`:** `proposedWeight`, sets, rep range, `targetRir`, readiness/performance summaries
- **Apply flow:** `GET .../apply-preview` + `POST .../apply` — updates **uncompleted sets only**; explicit approval required

**Tests:**
- `RuleBasedCoachRecommendationGeneratorTest` — readiness context in JSON
- `CoachBoundsCalculatorReadinessTest` — low readiness cannot exceed last weight
- `CoachApplyServiceTest` — completed sets unchanged; no mutation without apply

## §4 Dashboard "Today"

- Hero card: program context, readiness interpretation, coach link, Start/Continue
- Supporting cards: weekly workouts (goal ring **only** when active program has days), volume, prior-week delta
- Chart switcher: Working volume | Workouts | Estimated 1RM (exercise picker)
- Recent workouts (compact list)
- **Removed** hardcoded `weekGoal = 4`

## §5 Visual polish

- IBM Plex Mono on weights/reps/volume in touched pages
- Progress ring only when `program.days.length` derives a real weekly goal
- Existing harbor/mist/sage/sand tokens preserved

## Documentation

- `docs/DECISIONS.md` — six Batch 4 entries (storage, prefill, rest timer, PR criteria, readiness→bounds, 1RM tool)
- `docs/openapi.yaml` — new strength/coach-apply/set endpoints

## Intentionally NOT built (deferred)

- Muscle-soreness body-region mapping
- Full training profile system
- Bar/plate inventory
- Strength classification percentiles
- Six-tab stats overhaul
- Multi-tool AI architecture beyond the single 1RM tool

## Key files

| Area | Path |
|------|------|
| Migration | `src/main/resources/db/migration/V8__*.sql` |
| 1RM service | `src/main/java/com/gymsync/api/strength/service/Estimated1RmService.java` |
| PR detection | `src/main/java/com/gymsync/api/strength/service/PrDetectionService.java` |
| Coach apply | `src/main/java/com/gymsync/api/coach/service/CoachApplyService.java` |
| Active session | `frontend/src/pages/ActiveSessionPage.tsx` |
| Rest timer | `frontend/src/context/SessionRestTimerContext.tsx` |
| Strength page | `frontend/src/pages/StrengthPage.tsx` |
| Dashboard | `frontend/src/pages/DashboardPage.tsx` |
