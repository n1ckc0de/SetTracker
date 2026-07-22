# GymSync Frontend Redesign Plan

**Started:** 2026-07-20  
**Updated:** 2026-07-20 (gap closure pass)  
**Status:** Gap closure complete (2026-07-20)  
**Baseline (post-split):** `npm run build` — route chunks; main ~224 KB gzip ~70 KB; LineChart ~346 KB gzip ~100 KB. Backend Exercise Detail no-PR path fixed and covered by `ExerciseControllerTest`. Live smoke confirmed `GET /api/exercises/{id}/detail` → 200 with null PR when no sets.

## Current-state findings (verified against code)

| Area | Finding |
|------|---------|
| Stack | React 19 + Vite 8 + RR7 + Recharts + Lucide + STOMP; semantic CSS tokens |
| Shell | Grouped Train/Analyze/Coaching nav, Start Workout, mobile bottom nav, WS indicator |
| Readiness / Coach | Present and wired to APIs; polish remaining (chart / 3-col layout) |
| Media | Suggest → pending review → confirm; YouTube iframe embed; Wikimedia; `DECISIONS.md` |
| Exercise Detail 500 | Fixed in `ExerciseDetailService` (skip `personalRecord` when no sets) |
| Gaps remaining | Dashboard dominant chart + switcher; session previous-perf/PR/empty CTAs; shared Modal/RecommendationCard/ChartFrame; Coach 3-col; sidebar current program; readiness history chart; Import mobile steps; Vitest smoke tests |

## Architecture decisions

1. Incremental redesign — keep API clients and auth/WS.
2. Semantic tokens: train cyan, progress mint, caution coral, coach violet, danger red.
3. Icons: `lucide-react`.
4. Lazy routes for authenticated pages.
5. Media: YouTube iframe embed only; Wikimedia images; pending review before publish.
6. No fabricated metrics — backend-backed data only.

## Checklist (living)

- [x] Baseline audit recorded
- [x] Exercise Detail root-cause fix (backend)
- [x] Design tokens + core UI (PageHeader, MetricCard, ProgressRing, Skeleton, Toast, EmptyState)
- [x] Shell redesign (grouped nav, Start Workout, mobile bottom nav)
- [x] Lazy routes + bundle note
- [x] Media auto-source + review + DECISIONS
- [x] Shared Modal / RecommendationCard / ChartFrame
- [x] Dashboard dominant chart + metric switcher + recent volume
- [x] Active Session previous performance, PR feedback, continue/repeat empty CTAs
- [x] Coach 3-column desktop layout + RecommendationCard
- [x] Sidebar current program
- [x] Readiness history chart
- [x] Import Notes mobile step flow
- [x] Exercise detail error preserves header + retry
- [x] Program start stores current program for sidebar / continue
- [x] Vitest smoke tests + `npm test`
- [x] Docs + `npm run build` + `./mvnw` exercise detail coverage + live smoke

## Testing strategy

- Backend: detail-without-PR + media suggestion tests.
- Frontend: Vitest unit/smoke for Media helpers, greeting, Modal; CI keeps `npm run build`.
- Manual browser: shell, session, detail, coach, import.

## Accessibility / performance

Focus rings, labels, reduced motion, 44px targets, route splitting, click-to-load YouTube iframe.
