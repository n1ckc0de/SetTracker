# Equipment-Photo Workout Generation — Design

**Status:** Design only (future extension, `PROJECT_SPEC.md` §18). Not implemented.  
**Priority:** Only after Phases 0–9 and the AI-trainer design are settled.

## Goal

Allow a user to upload a photo of gym equipment, confirm detections, and receive a **suggested** workout draft that still requires human review before it becomes official history (same philosophy as notes import).

## Non-goals

- Fabricate exercise videos or copyrighted instructional media
- Auto-start workouts from detections without confirmation
- Store images longer than the declared retention policy without consent
- Replace human judgment about load selection or medical readiness

## High-level flow

```text
1. Client uploads image          POST /api/equipment/analyze
2. API stores object + enqueues async job
3. Vision model returns candidates with confidence scores
4. Client confirms equipment     POST /api/equipment/{jobId}/confirm
5. Rule engine proposes workout  POST /api/equipment/{jobId}/generate-workout
6. Result is a PENDING_REVIEW workout (source = EQUIPMENT_PHOTO, future enum)
7. User confirms or rejects via existing import-review endpoints pattern
```

## Proposed endpoint contract

### Upload / analyze

```http
POST /api/equipment/analyze
Content-Type: multipart/form-data
Authorization: Bearer <jwt>

file: <image>
experienceLevel: BEGINNER|INTERMEDIATE|ADVANCED
```

```json
{
  "jobId": "uuid",
  "status": "QUEUED",
  "pollUrl": "/api/equipment/{jobId}"
}
```

### Job status

```http
GET /api/equipment/{jobId}
```

```json
{
  "jobId": "uuid",
  "status": "READY_FOR_CONFIRMATION",
  "detections": [
    { "label": "barbell", "confidence": 0.91 },
    { "label": "squat_rack", "confidence": 0.84 }
  ],
  "disclaimer": "Detections are probabilistic. Confirm before generating a workout."
}
```

### Confirm detections

```http
POST /api/equipment/{jobId}/confirm
{
  "acceptedLabels": ["barbell", "squat_rack"],
  "rejectedLabels": []
}
```

### Generate workout draft

```http
POST /api/equipment/{jobId}/generate-workout
{
  "targetDurationMinutes": 45,
  "focus": "LEGS"
}
```

Returns a workout detail payload with `importStatus = PENDING_REVIEW`.

## Storage and validation

| Concern | Approach |
|---|---|
| Object storage | S3-compatible bucket (MinIO locally); never bake credentials into images |
| Image types | Allow `image/jpeg`, `image/png`, `image/webp` only |
| Size limit | Configurable (e.g. 5 MiB); reject larger uploads with 413 |
| Malware scanning | Optional ClamAV/async scanner before vision call; fail closed if required |
| Retention | Default delete raw images after N days or immediately after job completion; document in privacy section |
| Access control | Object keys scoped by `userId`; signed URLs short-lived |

## Vision-model integration

- Behind an interface (`EquipmentDetector`) with mock + real providers
- Require confidence scores; drop detections below a configurable threshold
- Never trust labels blindly — human confirmation is mandatory
- Timeouts, retries for transient failures only, circuit breaker optional

## Exercise recommendation rules (deterministic)

After confirmed equipment labels:

1. Map labels → allowed exercise catalog (curated table, not free-form LLM invention)
2. Filter by user experience level and optional focus category
3. Prefer exercises the user already has history with when ties occur
4. Attach **curated** form-media references from an allow-list owned by the project — never hallucinated YouTube links
5. Produce sets as a draft only; progressive-overload engine may optionally seed suggested weights from PRs

## Safety disclaimers

Every generate response must include:

> Equipment detection and workout suggestions are experimental and informational. They are not medical advice. Confirm form and load selection with a qualified trainer when unsure.

## Asynchronous processing

Use a DB-backed job table (`equipment_jobs`) with statuses `QUEUED`, `PROCESSING`, `READY_FOR_CONFIRMATION`, `CONFIRMED`, `GENERATED`, `FAILED`, `EXPIRED`. Polling is enough for v1; WebSocket job updates are optional later.

## Privacy

- Images are personal data — encrypt at rest in object storage when the provider supports it
- Do not use uploads to train third-party models unless the user opts in and the provider contract allows it
- Document retention and deletion in README / SECURITY when implemented

## Testing strategy (when built)

- Unit: content-type/size rejection
- Unit: confidence threshold filtering
- Integration: confirm → generate creates `PENDING_REVIEW` workout
- Integration: cross-user job access denied
- Contract: no copyrighted media URLs outside curated allow-list

## Decision

Keep this document as the authoritative design. Do not implement until the core GymSync API remains green and portfolio-ready.
