# Security

## Authentication

- Passwords stored as BCrypt hashes only
- Stateless JWT (HS256); secret from `JWT_SECRET` / `gymsync.jwt.secret`
- Login/register errors do not reveal whether an email exists

## Authorization

- Object ownership checked in services
- Inaccessible resources return **404** (not 403) where that avoids existence leaks for other users' custom exercises/workouts
- Cross-user mutations are covered by automated tests

## Webhooks

- HMAC-SHA256 over `timestamp + "." + rawBody`
- Constant-time signature compare
- Timestamp skew window (`gymsync.webhook.timestamp-tolerance-seconds`)
- Idempotent on `eventId`
- See [WEBHOOKS.md](WEBHOOKS.md)

## CORS

- Origins from `CORS_ALLOWED_ORIGINS` / `gymsync.cors.allowed-origins`
- No wildcard origins with credentials

## Actuator

- Public: `/actuator/health` (details limited)
- Metrics/prometheus require authentication
- Do not expose shutdown or env endpoints

## Logging hygiene

Never log: passwords, JWTs, `Authorization` headers, webhook secrets/signatures, LLM API keys.

## Secrets

Use `.env` locally (gitignored). Only `.env.example` placeholders are committed.
