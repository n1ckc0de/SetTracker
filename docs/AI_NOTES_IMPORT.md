# AI Workout-Notes Import

## Endpoints

```
POST  /api/notes/import
PATCH /api/workouts/{workoutId}/confirm-import
PATCH /api/workouts/{workoutId}/reject-import
```

## Flow

1. User pastes free text, e.g. `"Bench 3x8 135 lb, Squat 5x5 185 lb"`.
2. `NotesImportService` validates length (`gymsync.llm.max-raw-text-length`), then delegates
   parsing to a `WorkoutNotesParser`.
3. The parser returns a `ParsedWorkout` (a list of `ParsedSet`s: exercise name, weight, reps, set
   number). Every field is treated as **untrusted input**, whether it came from the deterministic
   parser or a real LLM.
4. Each parsed exercise name is normalized (`ExerciseNameNormalizer`) and matched against
   exercises visible to the user (global + their own). No match creates a new user-owned exercise
   (category `OTHER`); more than one match is rejected as ambiguous (`400`) rather than guessing.
5. A `Workout` is created via `Workout.createPendingImport`: `source = NOTES_IMPORT`,
   `importStatus = PENDING_REVIEW`, with `rawImportText` preserved for the user's own review.
6. The pending workout (with its sets) is returned for human review — nothing is silently
   promoted to official history.
7. The user must explicitly `confirm-import` or `reject-import`. Only a `CONFIRMED` import counts
   toward personal records or weekly statistics (see docs/API_DESIGN.md, spec §14/§15).

## Parser abstraction

```java
public interface WorkoutNotesParser {
    ParsedWorkout parse(String rawText);
}
```

Two implementations, selected by `gymsync.llm.provider` (`LLM_PROVIDER`):

| `gymsync.llm.provider` | Class | Notes |
|--------------------------|-------|-------|
| `rule-based` (default) | `RuleBasedWorkoutNotesParser` | Deterministic regex parser. No network calls, no API key. This is what runs in tests and local development by default, so the app and test suite work without a paid LLM account (spec §13). |
| `openai` | `LlmWorkoutNotesParser` | Optional OpenAI-compatible chat-completions client, enabled only when explicitly configured. |

Only one is active at a time (`@ConditionalOnProperty`).

### Rule-based parser

Supports two patterns per exercise, comma/newline/semicolon-separated:

- `<name> <sets>x<reps> <weight> [lb|kg]` → expands to one `ParsedSet` per set (e.g. `Bench 3x8
  135 lb` → three sets of 135x8).
- `<name> <weight>x<reps> [lb|kg]` → a single set (e.g. `Deadlift 225x5`).

Anything else is rejected with a clear `400` naming the unparsable segment — it never guesses.

### LLM-backed parser

Enabled with `LLM_PROVIDER=openai` plus `LLM_API_KEY`. Sends a system prompt
(`src/main/resources/prompts/workout-notes-import.txt`) requesting **strict JSON** matching:

```json
{
  "exercises": [
    { "name": "Bench Press", "sets": [ { "weight": 135, "reps": 8, "setNumber": 1 } ] }
  ]
}
```

Reliability/safety controls (all externalized, see `LlmProperties` / `.env.example`):

- Connect/request **timeout** (`gymsync.llm.timeout-ms`).
- Bounded **retries** (`gymsync.llm.max-retries`) — never retries a request the provider
  rejected as invalid (4xx), only transport-level failures.
- **API key never logged.**
- Response `content` may be wrapped in a Markdown code fence; it is stripped before parsing.
- Every field of the parsed JSON is validated before use: `exercises` must be a non-empty array
  bounded by `gymsync.llm.max-exercises`; each exercise needs a non-blank `name` and a non-empty
  `sets` array bounded by `gymsync.llm.max-sets-per-exercise`; each set must include `weight`,
  `reps`, and `setNumber`, all of which must be numerically sane (`weight >= 0`, `reps > 0`,
  `setNumber > 0`).
- Any structural problem — missing content, invalid JSON, missing/extra fields, hallucinated
  types, out-of-range counts, invalid numeric values — is rejected as a `400 Bad Request`, never
  silently coerced or defaulted. See `LlmWorkoutNotesParserTest` for the exact malformed-output
  cases covered.

## Data-quality / privacy stance

- Imports never bypass human review: `PENDING_REVIEW` workouts are excluded from PRs and weekly
  stats until explicitly confirmed.
- The original `rawImportText` is stored so the user can compare the AI's interpretation against
  what they actually typed — but this does mean free-text notes are retained in the database for
  as long as the workout row exists. Treat it like any other user-generated content; there is no
  separate retention policy yet (tracked in `docs/FUTURE_ROADMAP.md`).
- Ambiguous exercise names are surfaced as an error rather than silently picked, so a user notices
  and can rename/disambiguate rather than have a workout attributed to the wrong exercise.
