# API Design

Interactive OpenAPI docs: `http://localhost:8080/swagger-ui.html`

## Authentication

| Method | Path | Auth |
|--------|------|------|
| POST | `/api/auth/register` | Public |
| POST | `/api/auth/login` | Public |

Login returns a Bearer JWT. Pass `Authorization: Bearer <token>` on protected routes.

## Exercises

| Method | Path | Notes |
|--------|------|-------|
| GET | `/api/exercises` | Pagination, `query`, `category`, `sort` |
| POST | `/api/exercises` | Create user-owned exercise |
| GET | `/api/exercises/{id}` | Global or own only |
| PUT | `/api/exercises/{id}` | Own only |
| DELETE | `/api/exercises/{id}` | Own only |
| GET | `/api/exercises/{id}/pr` | Personal records |
| GET | `/api/exercises/{id}/next-session-recommendation` | Deterministic overload advice |

## Workouts

| Method | Path | Notes |
|--------|------|-------|
| POST | `/api/workouts` | Start active manual workout |
| GET | `/api/workouts` | History filters |
| GET | `/api/workouts/{id}` | Detail + sets |
| PATCH | `/api/workouts/{id}/complete` | Complete active |
| DELETE | `/api/workouts/{id}` | Soft-cancel |
| PATCH | `/api/workouts/{id}/confirm-import` | Confirm AI import |
| PATCH | `/api/workouts/{id}/reject-import` | Reject AI import |

## Sets

| Method | Path |
|--------|------|
| POST | `/api/workouts/{workoutId}/sets` |
| GET | `/api/workouts/{workoutId}/sets` |
| PUT | `/api/sets/{setId}` |
| DELETE | `/api/sets/{setId}` |

## Other

| Method | Path | Auth |
|--------|------|------|
| POST | `/api/webhooks/checkin` | HMAC signature |
| POST | `/api/notes/import` | JWT |
| GET | `/api/stats/weekly` | JWT |

## Error contract

See `ErrorResponse`: `timestamp`, `status`, `error`, `code`, `message`, `path`, `fieldErrors`, `correlationId`.

WebSocket destinations are documented in [WEBSOCKETS.md](WEBSOCKETS.md).
