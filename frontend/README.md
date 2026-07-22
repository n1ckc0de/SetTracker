# GymSync Frontend

React + TypeScript SPA for the GymSync workout API. Dark theme, sidebar navigation, and live
STOMP session updates.

## Requirements

- Node 20+
- GymSync API running at `http://localhost:8080` (see repo root README)

## Setup

```bash
cd frontend
npm install
npm run dev
```

Open [http://localhost:5173](http://localhost:5173). The Vite dev server proxies `/api` and `/ws`
to the backend, so browser calls stay same-origin during local development.

### Pointing at the backend without the proxy

Create `frontend/.env.local`:

```bash
VITE_API_BASE_URL=http://localhost:8080
VITE_WS_BASE_URL=ws://localhost:8080
```

Then restart `npm run dev`. Direct cross-origin calls require CORS to allow the Vite origin.

## CORS

The API must allow the frontend origin. Defaults now include Vite’s port:

```text
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173,http://localhost:8080
```

Configured in:

- `src/main/resources/application.yml`
- `compose.yaml`
- `.env.example`

Restart the Spring Boot app after changing CORS.

## Auth token storage

JWT + user profile are stored in **`sessionStorage`** (keys `gymsync.accessToken`, `gymsync.user`).
This survives refresh within the tab and clears when the tab closes. It is not written to
`localStorage`.

## Scripts

| Command | Purpose |
|---------|---------|
| `npm run dev` | Vite dev server on port 5173 |
| `npm run build` | Typecheck + production build |
| `npm test` | Vitest unit/smoke tests |
| `npm run preview` | Preview production build |

## End-to-end smoke path

1. Register / sign in
2. Submit a **Readiness** check-in; confirm it appears on the **Dashboard**
3. Create a custom **Exercise** with optional thumbnail / demo video / attribution
4. Build a **Program** with at least one day and exercises → **Start day** → confirm planned
   exercises appear on the active workout with **no fabricated sets**
5. Log real sets on **Active Session** → complete the workout
6. Open **Coach** (or Exercise Detail) → generate a recommendation → accept / modify / dismiss;
   send a chat message containing an injury keyword and confirm the fixed safety reply
7. **Import notes** → review → confirm → verify it counts toward stats
8. Open **Stats** — volume trend and category charts should reflect logged data
9. Confirm every sidebar item (Dashboard, Workouts, Active Session, Exercises, Programs,
   Readiness, Coach, Notes Import, Stats) loads a real page with no blank screens

## Design tokens

See `src/styles/tokens.css` and `docs/FRONTEND_REQUIREMENTS.md` in the repo root.
