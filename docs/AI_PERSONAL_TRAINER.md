# AI Personal Trainer — Architecture (Safe Summary Assistant)

**Status:** Design only (stretch goal, `PROJECT_SPEC.md` §17). Not implemented in the running API.  
**Priority:** After core Phases 0–9. Core application quality always wins.

## Goal

Provide a **limited, clearly labeled** assistant that summarizes information **already present** in the authenticated user's workout history. It must never act as a medical coach.

## Non-goals (hard prohibitions)

- Diagnose injuries or infer health conditions
- Prescribe supplements, medications, or diets
- Recommend dangerous loads or claim medical accuracy
- Present generated text as professional medical advice
- Autonomously modify workouts without human confirmation

## Proposed capability set (v1)

| Capability | Data source | Output style |
|---|---|---|
| Workout-duration summary | `started_at` / `ended_at` on confirmed completed workouts | Descriptive totals and trends |
| Strength-trend summary | Confirmed sets for selected exercises | Relative change, not prescriptions |
| Recently improved exercises | Week-over-week volume or PR deltas | Factual list with dates |
| Hydration reminder | User-configured preference only | Echo of user setting |
| Suggested trainer questions | Derived from gaps in logged history | Questions to discuss with a qualified trainer |

## Architecture

```text
Client
  │  GET /api/assistant/summary?week=...
  ▼
AssistantController  ──JWT──► AssistantService
                                  │
                                  ├─ StatsService / WorkoutRepository (read-only facts)
                                  ├─ AssistantPromptBuilder (strict template)
                                  └─ WorkoutNotesParser-style LLM port (optional)
                                         │
                                         ├─ RuleBasedAssistantSummarizer (default, deterministic)
                                         └─ LlmAssistantSummarizer (config-gated)
```

Reuse the existing LLM abstraction pattern from notes import:

- Interface behind configuration (`gymsync.assistant.provider=rule-based|openai`)
- Deterministic local implementation so the app and tests run without paid credentials
- Treat all model output as untrusted; validate against a closed schema
- Timeouts, limited retries, no logging of API keys

## Suggested endpoint contract

```http
GET /api/assistant/summary?from=2026-07-01&to=2026-07-07
Authorization: Bearer <jwt>
```

```json
{
  "label": "EXPERIMENTAL_SUMMARY",
  "disclaimer": "Informational only. Not medical or professional training advice.",
  "period": { "from": "2026-07-01", "to": "2026-07-07" },
  "facts": {
    "completedWorkouts": 3,
    "totalVolume": 12500.00,
    "recentlyImproved": ["Bench Press"]
  },
  "narrative": "You completed 3 workouts this period. Bench Press volume rose versus the prior week.",
  "suggestedQuestionsForATrainer": [
    "Should I adjust bench-press volume given the recent increase?"
  ]
}
```

`facts` must be computed by application code. The LLM (if enabled) may only rephrase `facts` into `narrative` — never invent numbers.

## Safety controls

1. Require authentication and scope to the caller's own data.
2. Prefix every response with a stable experimental label and disclaimer.
3. Reject prompts that ask for diagnosis, supplementation, or medical claims (keyword / classifier gate).
4. Cap input history size (e.g. last 90 days / N workouts).
5. Prefer the rule-based summarizer in production until evaluation criteria pass.
6. Keep an audit log of prompt template version + provider, never raw secrets.

## Testing plan (when implemented)

- Unit: rule-based summarizer produces expected narrative from fixed facts
- Unit: LLM output with hallucinated numbers is rejected
- Integration: cross-user access returns 404/403
- Contract: disclaimer always present

## Decision

Defer implementation. Documented here so the stretch goal has a complete design without delaying the core API.
