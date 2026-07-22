# GymSync Frontend Requirements

Browser UI for the GymSync API. The product is a **personal workout tracker**, not a marketing site.
Opens to login / authenticated app shell. Swagger remains available for API exploration.

## Scope

A React + TypeScript SPA under `frontend/` that exercises the backend end-to-end:

- Register / login (JWT)
- Dashboard (active session, weekly summary, recent workouts, PR highlights)
- Workouts history + detail; start / complete / cancel
- Active session with live STOMP updates and add-set form
- Exercises browse / create / edit / delete (customs)
- Notes import → pending review → confirm / reject
- Stats: weekly volume, PRs, progressive-overload recommendation

## Backend assumptions

- API base: `http://localhost:8080`
- Auth: `Authorization: Bearer <accessToken>`
- CORS must allow the Vite origin (default `http://localhost:5173`)
- WebSocket: `ws://localhost:8080/ws/sessions` (STOMP, no SockJS)

## WebSocket topics

| Destination | Purpose |
|-------------|---------|
| `/topic/user/{userId}/sessions` | Session started / completed for the signed-in user |
| `/topic/session/{workoutId}` | Set logged / updated / deleted; session lifecycle for one workout |

CONNECT with native header `Authorization: Bearer <token>` (or `access_token`).

Event envelope: `{ eventId, eventType, occurredAt, workoutId, data }` where `eventType` is one of
`SESSION_STARTED`, `SET_LOGGED`, `SET_UPDATED`, `SET_DELETED`, `SESSION_COMPLETED`.

## Design system

Dark theme only. Tokens and typography are defined in `frontend/src/styles/tokens.css` and must not
be replaced with a generic UI-kit default theme.

Signature element: on the active-session view, a thin top line on the session card pulses while the
WebSocket is connected and the workout is `ACTIVE`; static when completed. Honor `prefers-reduced-motion`.

## Auth token storage

Use **sessionStorage** for the JWT in local development (survives refresh within the tab; cleared
when the tab closes). Document this in `frontend/README.md`. Do not use `localStorage`.

## Non-goals

- No marketing / landing page
- No fabricated stats — all numbers from API
- No webhook UI (HMAC is a machine integration)
