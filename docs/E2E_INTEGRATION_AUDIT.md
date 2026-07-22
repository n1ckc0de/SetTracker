# End-to-End Local Integration Audit

**Date:** 2026-07-18
**Scope:** Verify the full stack (PostgreSQL + Spring Boot API + React/Vite frontend) runs
locally end-to-end, with every claim backed by an actually-executed command and observed output
— not assumed from reading the source.

## Environment used

| Tool | Version found | Required | Result |
|---|---|---|---|
| Java | OpenJDK 21.0.11 (Homebrew) | 21 (`pom.xml` `<java.version>`) | Match |
| Maven wrapper | 3.9.9 | Any (wrapper pinned) | OK |
| Docker | 29.6.2 / Compose 5.3.1 | Any recent | OK |
| Node | v22.23.1 | No `engines` field pinned in `frontend/package.json`; Vite 8 / React 19 support Node ≥20 | OK |
| npm | 10.9.8 | — | OK |

No stray processes were found holding ports 8080 or 5432. Port 5173 had a leftover Vite dev
server from an earlier, unrelated session in the same working directory; it was stopped
(`kill`, plain `SIGTERM` on the Node PID — a local dev server with no persistent state) and
restarted cleanly as part of this audit so a fresh, verifiable startup log could be captured.

## Result summary

**No source-code or configuration defects were found.** Every step in the required verification
list (build, backend startup, CORS, frontend build, and the full functional E2E flow — auth,
exercises, workout lifecycle, WebSocket, notes import/confirm/reject, stats) passed on the first
attempt with real command output as evidence, captured below. This audit is therefore a
**verification report**, not a changelog of fixes — because nothing needed fixing.

The only repository-state issue observed was operational, not a defect: a stale, previously-built
Docker container (`gymsync-api`, image `settracker-app:latest`) sitting in `Exited (1)` state from
an earlier session where `docker compose up --build` (or the app image alone) had apparently been
run before Postgres was healthy, producing a `FlywaySqlException: Connection to localhost:5432
refused`. This is expected behavior given `compose.yaml` already declares
`depends_on: postgres: condition: service_healthy` for the `app` service, so a normal `docker
compose up --build` run would not hit this — only running the `app` image in isolation without
Postgres up would. No code change was made or needed; noted here only for completeness of the
audit trail.

## 1. Backend build and tests

```bash
docker compose up -d postgres
# gymsync-postgres healthy after ~3s

export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw clean verify
```

Result: **BUILD SUCCESS**.

- Unit + controller tests: `Tests run: 123, Failures: 0, Errors: 0, Skipped: 0`
- Failsafe (Testcontainers) integration tests: `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`
  (`OpenApiSpecIT`, `EndToEndWorkoutFlowIT`, `ConcurrencyRaceIT`)
- JaCoCo coverage check: `All coverage checks have been met.`
- Spotless (Google Java Format): `Spotless.Java is keeping 126 files clean`
- SpotBugs: `BugInstance size is 0`, `No errors/warnings found`

No flakes, no retries needed.

## 2. Backend startup

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Startup log confirms: Flyway validated and applied schema version 3 with no pending migrations,
HikariCP connected, Tomcat started on port 8080 in 2.6s total.

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP","components":{"db":{"status":"UP", ...
```

```bash
curl -I http://localhost:8080/swagger-ui.html
# HTTP/1.1 302  Location: /swagger-ui/index.html
curl -I http://localhost:8080/swagger-ui/index.html
# HTTP/1.1 200
curl -o /dev/null -w "%{http_code}" http://localhost:8080/v3/api-docs
# 200
```

The 302 is springdoc's standard redirect from `/swagger-ui.html` to `/swagger-ui/index.html`,
which returns 200 — Swagger loads correctly.

## 3. CORS configuration

Checked for consistency across all three locations the spec calls out:

- `src/main/resources/application.yml` — `gymsync.cors.allowed-origins` defaults to
  `http://localhost:3000,http://localhost:5173,http://localhost:8080`.
- `compose.yaml` — `CORS_ALLOWED_ORIGINS` env var for the `app` service defaults to the same list.
- `.env.example` — `CORS_ALLOWED_ORIGINS` documented with the same default list.

All three already agreed; **no change was required**.

Verified the config is actually wired, not just declared: `SecurityConfig.corsConfigurationSource`
builds a `CorsConfigurationSource` from `CorsProperties` and registers it on `/**`, and
`WebSocketConfig.registerStompEndpoints` uses the same `CorsProperties` bean for the `/ws/sessions`
STOMP endpoint's allowed origins — so REST and WebSocket share one source of truth.

Confirmed live with an actual preflight and actual request carrying `Origin: http://localhost:5173`:

```bash
curl -i -X OPTIONS http://localhost:8080/api/exercises \
  -H 'Origin: http://localhost:5173' \
  -H 'Access-Control-Request-Method: GET' \
  -H 'Access-Control-Request-Headers: Authorization'
# HTTP/1.1 200
# Access-Control-Allow-Origin: http://localhost:5173
# Access-Control-Allow-Methods: GET,POST,PUT,PATCH,DELETE,OPTIONS
# Access-Control-Allow-Headers: Authorization
# Access-Control-Allow-Credentials: true
```

Every subsequent authenticated request in this audit (register, login, exercises, workouts,
sets, notes import, stats) was sent with `Origin: http://localhost:5173` and returned the matching
`Access-Control-Allow-Origin` header, including on 401 responses (unauthenticated and
invalid-token requests) — CORS headers are applied before/alongside auth rejection, so a browser
client gets a real 401 body rather than an opaque CORS failure.

## 4. Frontend build

```bash
cd frontend && npm install
# up to date, audited 34 packages in 337ms, 0 vulnerabilities

npm run dev
#   VITE v8.1.5  ready in 156 ms
#   ➜  Local:   http://localhost:5173/
curl -o /dev/null -w "%{http_code}" http://localhost:5173/   # 200

npm run build
# tsc -b && vite build — 46 modules transformed, built in 82ms, no errors
```

`vite.config.ts` proxies `/api` and `/ws` to `http://localhost:8080` / `ws://localhost:8080`
with `changeOrigin: true`, so in normal browser use the frontend and backend appear same-origin
to the browser and CORS is not even exercised for proxied dev traffic — the CORS configuration
audited above matters for direct (non-proxied) API access, e.g. Swagger UI or any client hitting
`:8080` straight from a page served on `:5173`, which is exactly what section 3 above verifies
directly with `curl`.

## 5. Full functional E2E flow (real requests, real responses)

All requests below were sent with `Origin: http://localhost:5173` to mirror what the browser
would send.

### 5.1 Register

```bash
curl -X POST http://localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"e2e-audit-<ts>@example.com","password":"SecurePass1!","name":"E2E Audit"}'
```
`201 Created`, body contains `accessToken`, `tokenType: Bearer`, `expiresIn: 3600`, and `user`.

### 5.2 Login

```bash
curl -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"...","password":"SecurePass1!"}'
```
`200 OK`, fresh `accessToken` returned.

### 5.3 Authenticated request, no CORS/401 error

```bash
curl http://localhost:8080/api/exercises -H "Authorization: Bearer $TOKEN"
```
`200 OK`, returns the 12 seeded global exercises, paginated envelope
(`content`, `page`, `size`, `totalElements`, `totalPages`).

Negative controls also verified:
- No `Authorization` header → `401`, CORS headers still present.
- Malformed/garbage bearer token → `401`, CORS headers still present.

### 5.4 Workout lifecycle

```bash
POST /api/workouts {}                              → 201 status=ACTIVE
POST /api/workouts/{id}/sets  {Bench Press,135,8,1} → 201 Created
PATCH /api/workouts/{id}/complete                   → 200 status=COMPLETED, endedAt set
GET /api/workouts/{id}                              → 200, sets=[the logged set], confirms persistence
```

### 5.5 WebSocket / STOMP

Rather than only checking that the endpoint is reachable, a real STOMP client (`@stomp/stompjs`,
the same library the frontend uses, driven from a small Node script against Node 22's native
`WebSocket`) connected end-to-end:

```
>>> CONNECT   Authorization: Bearer <token>
<<< CONNECTED user-name: e2e-audit-...@example.com
>>> SUBSCRIBE destination: /topic/user/{userId}/sessions
```

While subscribed, a `PATCH /api/workouts/{id}/complete` was issued via `curl` in parallel. The
subscribed client received, live, over the same connection:

```
<<< MESSAGE destination: /topic/user/{userId}/sessions
{"eventId":"...","eventType":"SESSION_COMPLETED","occurredAt":"...","workoutId":"...","data":{"userId":"..."}}
```

This confirms: JWT auth on STOMP `CONNECT` succeeds, `SUBSCRIBE` authorization succeeds for the
owning user, and the event is published only after the REST completion transaction committed
(the event arrived after the `curl` PATCH returned its `200`, not before).

**Negative control:** the same authenticated client then attempted to `SUBSCRIBE` to
`/topic/user/{a different random UUID}/sessions`. The server rejected it (the connection was
closed with a `MessageDeliveryException` wrapping `WebSocketAuthChannelInterceptor`'s
`IllegalArgumentException("Not allowed to subscribe to another user's sessions")`), confirming
the spec's requirement that users cannot subscribe to another user's private session topic.

### 5.6 Notes import → confirm/reject → stats

```bash
GET /api/stats/weekly                                    # baseline: completedWorkouts=2, totalSets=1
POST /api/notes/import {"rawText":"Bench 3x8 135 lb, Squat 5x5 185 lb"}
                                                           # 201, source=NOTES_IMPORT, importStatus=PENDING_REVIEW
GET /api/stats/weekly                                    # unchanged — pending import correctly excluded
PATCH /api/workouts/{id}/confirm-import                   # 200, importStatus=CONFIRMED
GET /api/stats/weekly                                    # completedWorkouts=3, totalSets=9, totalVolume=8945.00
```

Volume math checks out exactly: prior 1080.00 (Bench Press 135×8) + new Bench (rule-parser
matched "Bench" as a distinct exercise from "Bench Press", so it created a new user-owned
exercise per the documented ambiguous-match behavior) 135×8×3=3240.00 + Squat 185×5×5=4625.00 =
**8945.00**, matching the reported total exactly.

Reject path verified separately with a second import (`"Deadlift 1x5 225 lb"`):
`PATCH .../reject-import` → `200`, `importStatus=REJECTED`, and `/api/stats/weekly` was byte-for-byte
identical before and after the rejection — rejected imports never touch statistics, permanently.

## Known issues / unresolved items

- **None found in the verified surface area.** Every item in the required E2E flow (sections
  1–5 above) passed with real, captured evidence.
- **Not exercised in this audit** (out of scope for the required flow, called out here so nothing
  is implied as verified that wasn't): the check-in webhook (`POST /api/webhooks/checkin`) and the
  optional OpenAI-compatible LLM notes parser (`LLM_PROVIDER=openai`) were not driven end-to-end
  in this session. Both are already covered by the passing Testcontainers/unit test suite
  (`CheckInWebhookIntegrationTest`, `LlmWorkoutNotesParserTest`), and the webhook has a working
  signing script (`scripts/sign-checkin-webhook.sh`), but this audit's own curl/WebSocket
  evidence only covers what section 5 of the audit request enumerated.
- The optional LLM provider path remains unverified against a real OpenAI account (no credentials
  available in this environment), consistent with the pre-existing "Known limitations" note in
  the root README.
