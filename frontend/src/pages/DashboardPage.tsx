import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { ApiError } from "../api/http";
import * as coachApi from "../api/coachApi";
import { listExercises } from "../api/exercisesApi";
import { getProgram } from "../api/programsApi";
import * as readinessApi from "../api/readinessApi";
import * as statsApi from "../api/statsApi";
import * as workoutsApi from "../api/workoutsApi";
import type {
  CoachRecommendation,
  Exercise,
  ReadinessCheckIn,
  VolumeTrend,
  WeeklyStats,
  Workout,
  WorkoutDetail,
} from "../api/types";
import { useAuth } from "../auth/AuthContext";
import { getCurrentProgram } from "../lib/currentProgram";
import { ChartFrame } from "../components/ChartFrame";
import { RecommendationCard } from "../components/RecommendationCard";
import { useToast } from "../components/Toast";
import {
  EmptyState,
  ErrorBanner,
  LivePulseBar,
  LoadingBlock,
  MetricCard,
  PageHeader,
  ProgressRing,
  StatusBadge,
  formatDateTime,
  formatDuration,
  formatNumber,
} from "../components/ui";
import { useSessionSocket } from "../ws/SessionSocketContext";

function greetingForHour(hour: number): string {
  if (hour < 12) return "Good morning";
  if (hour < 17) return "Good afternoon";
  return "Good evening";
}

function readinessScore(checkIn: ReadinessCheckIn | null): number | null {
  if (!checkIn) return null;
  const parts: number[] = [];
  if (checkIn.sleepQuality != null) parts.push(checkIn.sleepQuality);
  if (checkIn.energyLevel != null) parts.push(checkIn.energyLevel);
  if (checkIn.soreness != null) parts.push(6 - checkIn.soreness);
  if (checkIn.stress != null) parts.push(6 - checkIn.stress);
  if (parts.length === 0) return null;
  return Math.round((parts.reduce((a, b) => a + b, 0) / parts.length) * 20);
}

function readinessLine(checkIn: ReadinessCheckIn | null): string {
  const score = readinessScore(checkIn);
  if (score == null) return "Check in before training for readiness-aware coaching.";
  if (score >= 75) return "Signals look strong — good day to push working sets.";
  if (score >= 50) return "Moderate readiness — consider repeating last loads.";
  return "Recovery looks low — favor conservative loads and higher RIR.";
}

function toNum(value: number | string): number {
  return typeof value === "number" ? value : Number(value);
}

type ChartMetric = "volume" | "workouts" | "e1rm";

export function DashboardPage() {
  const { user } = useAuth();
  const { push } = useToast();
  const { connected, lastUserEvent } = useSessionSocket();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [active, setActive] = useState<WorkoutDetail | null>(null);
  const [recent, setRecent] = useState<Workout[]>([]);
  const [recentDetails, setRecentDetails] = useState<Record<string, WorkoutDetail>>({});
  const [weekly, setWeekly] = useState<WeeklyStats | null>(null);
  const [trend, setTrend] = useState<VolumeTrend | null>(null);
  const [readiness, setReadiness] = useState<ReadinessCheckIn | null>(null);
  const [coach, setCoach] = useState<CoachRecommendation | null>(null);
  const [starting, setStarting] = useState(false);
  const [chartMetric, setChartMetric] = useState<ChartMetric>("volume");
  const [weekGoal, setWeekGoal] = useState<number | null>(null);
  const [exercises, setExercises] = useState<Exercise[]>([]);
  const [e1rmExerciseId, setE1rmExerciseId] = useState("");
  const [e1rmTrend, setE1rmTrend] = useState<Array<{ week: string; e1rm: number }>>([]);

  const todayLabel = useMemo(
    () =>
      new Date().toLocaleDateString(undefined, {
        weekday: "long",
        month: "long",
        day: "numeric",
      }),
    [],
  );

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [activePage, recentPage, stats, exercisePage] = await Promise.all([
        workoutsApi.listWorkouts({ status: "ACTIVE", size: 1 }),
        workoutsApi.listWorkouts({ size: 4 }),
        statsApi.weeklyStats(),
        listExercises({ size: 50, sort: "name,asc" }),
      ]);
      setWeekly(stats);
      setRecent(recentPage.content);
      setExercises(exercisePage.content);
      setE1rmExerciseId((c) => c || exercisePage.content[0]?.id || "");

      const details = await Promise.all(
        recentPage.content.map(async (w) => {
          try {
            return await workoutsApi.getWorkout(w.id);
          } catch {
            return null;
          }
        }),
      );
      const map: Record<string, WorkoutDetail> = {};
      for (const d of details) {
        if (d) map[d.id] = d;
      }
      setRecentDetails(map);

      if (activePage.content[0]) {
        setActive(await workoutsApi.getWorkout(activePage.content[0].id));
      } else {
        setActive(null);
      }

      try {
        setTrend(await statsApi.volumeTrend(8));
      } catch {
        setTrend(null);
      }

      try {
        setReadiness(await readinessApi.latestReadiness());
      } catch (err) {
        if (err instanceof ApiError && err.status === 404) {
          setReadiness(null);
        } else {
          throw err;
        }
      }

      try {
        const proposed = await coachApi.listRecommendations("PROPOSED");
        setCoach(proposed[0] ?? null);
      } catch {
        setCoach(null);
      }

      const currentProgram = getCurrentProgram();
      if (currentProgram?.id) {
        try {
          const program = await getProgram(currentProgram.id);
          setWeekGoal(program.days.length > 0 ? program.days.length : null);
        } catch {
          setWeekGoal(null);
        }
      } else {
        setWeekGoal(null);
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to load dashboard.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!lastUserEvent) return;
    void load();
  }, [lastUserEvent, load]);

  useEffect(() => {
    if (chartMetric !== "e1rm" || !e1rmExerciseId) {
      setE1rmTrend([]);
      return;
    }
    void statsApi
      .prHistory(e1rmExerciseId)
      .then((history) => {
        const points = history.points.slice(-8).map((p) => ({
          week: p.at.slice(5, 10),
          e1rm: toNum(p.estimatedOneRepMax),
        }));
        setE1rmTrend(points);
      })
      .catch(() => setE1rmTrend([]));
  }, [chartMetric, e1rmExerciseId]);

  async function startWorkout() {
    setStarting(true);
    setError(null);
    try {
      const workout = await workoutsApi.startWorkout();
      setActive(await workoutsApi.getWorkout(workout.id));
      push("Workout started", "success");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not start workout.");
    } finally {
      setStarting(false);
    }
  }

  const firstName = user?.name?.split(/\s+/)[0] || "athlete";
  const readyPct = readinessScore(readiness);
  const weekDone = weekly?.completedWorkouts ?? 0;
  const currentProgram = getCurrentProgram();
  const volumePoints = trend?.points ?? [];
  const chartData = volumePoints.map((p) => ({
    week: p.weekStart.slice(5),
    volume: toNum(p.totalVolume),
    workouts: toNum(p.totalVolume) > 0 ? 1 : 0,
  }));
  const hasChartData =
    chartMetric === "e1rm"
      ? e1rmTrend.some((d) => d.e1rm > 0)
      : chartData.some((d) => (chartMetric === "volume" ? d.volume > 0 : d.workouts > 0));

  const chartSeries: Array<Record<string, string | number>> =
    chartMetric === "e1rm" ? e1rmTrend : chartData;

  if (loading) {
    return <LoadingBlock label="Loading dashboard…" />;
  }

  return (
    <div className="stack">
      <PageHeader title={`${greetingForHour(new Date().getHours())}, ${firstName}`} subtitle={todayLabel} />

      {error ? <ErrorBanner message={error} onRetry={() => void load()} /> : null}

      <section className="card stack hero-today">
        <div className="row" style={{ justifyContent: "space-between", alignItems: "flex-start" }}>
          <div className="stack" style={{ gap: "0.35rem" }}>
            <h2 style={{ margin: 0 }}>Today</h2>
            <p className="muted" style={{ margin: 0 }}>
              {currentProgram
                ? `Program: ${currentProgram.name}${currentProgram.dayName ? ` · ${currentProgram.dayName}` : ""}`
                : "No program day selected — start a workout or pick a program day."}
            </p>
          </div>
          {active ? (
            <Link className="btn btn-primary" to="/session">
              Continue workout
            </Link>
          ) : (
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => void startWorkout()}
              disabled={starting}
            >
              {starting ? "Starting…" : "Start workout"}
            </button>
          )}
        </div>

        <div className="grid-3" style={{ marginTop: "0.75rem" }}>
          <div>
            <div className="muted">Session</div>
            {active ? (
              <>
                <div className="mono">{formatDuration(active.startedAt)}</div>
                <LivePulseBar live={connected && active.status === "ACTIVE"} />
                <div className="muted">{active.sets.length} sets logged</div>
              </>
            ) : (
              <div className="muted">No active session</div>
            )}
          </div>
          <div>
            <div className="muted">Readiness</div>
            <div className="mono">{readyPct != null ? `${readyPct}%` : "—"}</div>
            <div className="muted">{readinessLine(readiness)}</div>
            <Link to="/readiness" className="btn btn-ghost" style={{ marginTop: "0.35rem", minHeight: 36 }}>
              {readiness ? "Update check-in" : "Check in"}
            </Link>
          </div>
          <div>
            <div className="muted">Coach</div>
            {coach ? (
              <>
                <div style={{ fontSize: "var(--text-sm)" }}>{coach.reasoning.slice(0, 120)}…</div>
                <Link to="/coach" className="btn btn-coach" style={{ marginTop: "0.35rem", minHeight: 36 }}>
                  View / apply
                </Link>
              </>
            ) : (
              <>
                <div className="muted">No open recommendation</div>
                <Link to="/coach" className="btn btn-ghost" style={{ marginTop: "0.35rem", minHeight: 36 }}>
                  Open coach
                </Link>
              </>
            )}
          </div>
        </div>
      </section>

      <section className="grid-3">
        <MetricCard
          label="Weekly workouts"
          accent="progress"
          value={
            weekGoal != null ? (
              <div className="row" style={{ gap: "1rem", alignItems: "center" }}>
                <ProgressRing
                  value={weekDone}
                  max={weekGoal}
                  size={72}
                  stroke={7}
                  color="var(--color-progress)"
                  label={`${weekDone}/${weekGoal}`}
                />
                <span className="mono">{weekDone} sessions</span>
              </div>
            ) : (
              <span className="mono">{weekDone}</span>
            )
          }
          hint={
            weekly
              ? `${formatNumber(weekly.totalVolume, 0)} vol · prior week ${formatNumber(weekly.comparisonWithPriorWeek.volumeChangePercent ?? 0, 1)}%`
              : "Log workouts to unlock weekly comparisons"
          }
        />
        <MetricCard
          label="This week volume"
          accent="train"
          value={<span className="mono">{weekly ? formatNumber(weekly.totalVolume, 0) : "—"}</span>}
          hint={`${weekly?.totalSets ?? 0} sets · ${weekly?.totalReps ?? 0} reps`}
        />
        <MetricCard
          label="Prior week delta"
          accent="caution"
          value={
            <span className="mono">
              {weekly?.comparisonWithPriorWeek.volumeChangePercent != null
                ? `${formatNumber(weekly.comparisonWithPriorWeek.volumeChangePercent, 1)}%`
                : "—"}
            </span>
          }
          hint="Working volume vs prior week (confirmed sets only)"
        />
      </section>

      <ChartFrame
        title="Training trend"
        subtitle="One metric at a time — confirmed working volume by default"
        summary={
          hasChartData
            ? `Showing ${chartMetric} across recent weeks.`
            : "No trend data yet."
        }
        actions={
          <div className="row" style={{ gap: "0.4rem", flexWrap: "wrap" }}>
            {(
              [
                ["volume", "Working volume"],
                ["workouts", "Workouts"],
                ["e1rm", "Estimated 1RM"],
              ] as const
            ).map(([value, label]) => (
              <button
                key={value}
                type="button"
                className={`chip${chartMetric === value ? " active" : ""}`}
                onClick={() => setChartMetric(value)}
              >
                {label}
              </button>
            ))}
            {chartMetric === "e1rm" ? (
              <select
                value={e1rmExerciseId}
                onChange={(e) => setE1rmExerciseId(e.target.value)}
                aria-label="Exercise for e1RM trend"
              >
                {exercises.map((ex) => (
                  <option key={ex.id} value={ex.id}>
                    {ex.name}
                  </option>
                ))}
              </select>
            ) : null}
            <Link to="/stats" className="btn btn-ghost" style={{ minHeight: 36, padding: "0.35rem 0.7rem" }}>
              Full stats
            </Link>
          </div>
        }
        empty={
          !hasChartData ? (
            <EmptyState
              title="No data yet"
              description="Complete workouts to populate this chart."
              action={
                <Link to="/session" className="btn btn-primary">
                  Start training
                </Link>
              }
            />
          ) : undefined
        }
      >
        {hasChartData ? (
          <div style={{ width: "100%", height: 240 }}>
            <ResponsiveContainer>
              <LineChart data={chartSeries}>
                <CartesianGrid stroke="var(--color-border)" strokeDasharray="3 3" />
                <XAxis dataKey="week" stroke="var(--color-text-muted)" fontSize={12} />
                <YAxis stroke="var(--color-text-muted)" fontSize={12} />
                <Tooltip
                  contentStyle={{
                    background: "var(--color-surface-raised)",
                    border: "1px solid var(--color-border)",
                    borderRadius: 8,
                  }}
                />
                <Line
                  type="monotone"
                  dataKey={chartMetric === "e1rm" ? "e1rm" : chartMetric}
                  stroke="var(--color-train)"
                  strokeWidth={2.5}
                  dot={{ r: 3 }}
                />
              </LineChart>
            </ResponsiveContainer>
          </div>
        ) : null}
      </ChartFrame>

      <section className="grid-2">
        <div className="card stack">
          <div className="row" style={{ justifyContent: "space-between" }}>
            <h2>Recent workouts</h2>
            <Link to="/workouts" className="btn btn-ghost" style={{ minHeight: 36, padding: "0.35rem 0.7rem" }}>
              All
            </Link>
          </div>
          {recent.length === 0 ? (
            <EmptyState
              title="No workouts yet"
              description="Start a blank session or pull a day from a program."
              action={
                <button type="button" className="btn btn-primary" onClick={() => void startWorkout()} disabled={starting}>
                  Start workout
                </button>
              }
            />
          ) : (
            <div className="stack" style={{ gap: "0.65rem" }}>
              {recent.map((workout) => {
                const detail = recentDetails[workout.id];
                const volume = detail
                  ? detail.sets.reduce((sum, s) => sum + toNum(s.weight) * s.reps, 0)
                  : null;
                return (
                  <Link key={workout.id} to={`/workouts/${workout.id}`} className="card-link set-card">
                    <div>
                      <div className="mono" style={{ fontWeight: 650 }}>
                        {formatDateTime(workout.startedAt)}
                      </div>
                      <div className="muted" style={{ fontSize: "var(--text-sm)", marginTop: "0.2rem" }}>
                        {formatDuration(workout.startedAt, workout.endedAt)}
                        {volume != null ? ` · ${formatNumber(volume, 0)} vol` : ""}
                      </div>
                    </div>
                    <StatusBadge status={workout.status} />
                  </Link>
                );
              })}
            </div>
          )}
        </div>

        <div className="stack">
          <div className="row" style={{ justifyContent: "space-between" }}>
            <h2>Coach insight</h2>
            <Link to="/coach" className="btn btn-ghost" style={{ minHeight: 36, padding: "0.35rem 0.7rem" }}>
              Coach
            </Link>
          </div>
          {coach ? (
            <RecommendationCard
              recommendation={coach}
              compact
              actions={
                <Link to="/coach" className="btn btn-coach">
                  Review recommendation
                </Link>
              }
            />
          ) : (
            <EmptyState
              title="No proposed advice"
              description="Generate a recommendation after logging sessions and readiness."
              action={
                <Link to="/coach" className="btn btn-coach">
                  Open coach
                </Link>
              }
            />
          )}
        </div>
      </section>
    </div>
  );
}
