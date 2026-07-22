# Webhooks

## Endpoint

```
POST /api/webhooks/checkin
```

Public route (no JWT) — authenticated instead by an HMAC signature, since the caller is a
third-party gym check-in provider, not a logged-in user.

## Request

```json
{
  "eventId": "turnstile-event-12345",
  "userId": "5b1e...-uuid",
  "source": "gym-turnstile-mock",
  "occurredAt": "2026-07-17T20:30:00Z"
}
```

- `eventId` — required, unique per delivery. This is the idempotency key.
- `userId` — required, must reference an existing user.
- `source` — required, free-text label (e.g. `gym-turnstile-mock`).
- `occurredAt` — optional; defaults to the time the webhook was received if omitted.

## Required headers

| Header | Description |
|--------|--------------|
| `X-Webhook-Timestamp` | Unix epoch seconds when the request was signed |
| `X-Webhook-Signature` | Hex-encoded HMAC-SHA256, optionally prefixed with `sha256=` |

## Signing scheme

```
signature = hex( HMAC-SHA256( secret, "<timestamp>." + rawRequestBody ) )
```

- `secret` is `gymsync.webhook.secret` / `WEBHOOK_SECRET`, shared out-of-band with the provider.
- The signature covers the **exact raw bytes** of the request body — not a re-serialized or
  reformatted version — so signing must happen against the bytes that will actually be sent.
- Comparison is constant-time (`MessageDigest.isEqual`) to avoid timing side-channels
  (`WebhookSignatureValidator`).
- Requests whose `X-Webhook-Timestamp` is more than
  `gymsync.webhook.timestamp-tolerance-seconds` (default 300s) away from "now" — in either
  direction — are rejected as `401`. This bounds the replay window: even a captured
  signature+body pair becomes useless once the tolerance window passes.

## Generating a signed request locally

```bash
WEBHOOK_SECRET=change-me-webhook-secret ./scripts/sign-checkin-webhook.sh \
  --event-id turnstile-evt-1 --user-id <uuid> --send
```

Omit `--send` to just print the `curl` command (and computed timestamp/signature) without
sending it. See the script's header comment for all options.

## Processing semantics

1. **Signature validation** happens before anything else, against the cached raw body (see
   `WebhookBodyCachingFilter` / `CachedBodyHttpServletRequest`) — the body is read once by a
   servlet filter and cached, because the controller needs the exact raw bytes for signature
   verification but also needs to deserialize it as JSON afterward.
2. **Idempotency**: if `eventId` has already been recorded, the handler returns the existing
   result (`200 OK`, `status: DUPLICATE`) without creating a second workout — this is what makes
   provider retries safe (see docs/CONCURRENCY.md).
3. **Unknown user**: `404 Not Found`.
4. **Active-workout conflict**: if the user already has an `ACTIVE` workout, the event is
   recorded as `REJECTED` and the response is `409 Conflict` — a check-in never interrupts a
   session already in progress.
5. **Success**: a new `ACTIVE`, `WEBHOOK`-sourced workout is created, the `CheckInEvent` is
   linked to it (`triggeredWorkout`), and a `SESSION_STARTED` WebSocket event is broadcast
   **after** the transaction commits. Response is `201 Created`.

## Retry behavior

Providers are expected to retry on network failure/timeout using the **same** `eventId`. Any
number of retries with the same `eventId` after a successful `201` will return `200 OK` /
`DUPLICATE` and never create a second workout. Retrying with a **new** `eventId` for the same
underlying gym visit is a provider-side bug outside this API's control — document integrations
to always generate `eventId` once per physical check-in.

## Auditing

Every webhook delivery — accepted, duplicate, rejected, or referencing an unknown user — is
persisted as a `CheckInEvent` row (`status`, `failureReason`, `receivedAt`) for audit purposes.
No webhook secret or raw signature is ever stored or logged.
