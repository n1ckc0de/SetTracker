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
import { listExercises } from "../api/exercisesApi";
import * as statsApi from "../api/statsApi";
import type {
  Exercise,
  NextSessionRecommendation,
  PersonalRecord,
  PrHistory,
  VolumeTrend,
  WeeklyStats,
} from "../api/types";
import {
  EmptyState,
  ErrorBanner,
  LoadingBlock,
  MetricCard,
  PageHeader,
  formatDate,
  formatNumber,
} from "../components/ui";

function toNum(value: number | string): number {
  return typeof value === "number" ? value : Number(value);
}

export function StatsPage() {
  const [weekly, setWeekly] = useState<WeeklyStats | null>(null);
  const [trend, setTrend] = useState<VolumeTrend | null>(null);
  const [exercises, setExercises] = useState<Exercise[]>([]);
  const [exerciseId, setExerciseId] = useState("");
  const [pr, setPr] = useState<PersonalRecord | null>(null);
  const [prSeries, setPrSeries] = useState<PrHistory | null>(null);
  const [rec, setRec] = useState<NextSessionRecommendation | null>(null);
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [detailError, setDetailError] = useState<string | null>(null);

  const loadWeekly = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [stats, volumeTrend, exercisePage] = await Promise.all([
        statsApi.weeklyStats(),
        statsApi.volumeTrend(12),
        listExercises({ size: 100, sort: "name,asc" }),
      ]);
      setWeekly(stats);
      setTrend(volumeTrend);
      setExercises(exercisePage.content);
      setExerciseId((current) => current || exercisePage.content[0]?.id || "");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to load weekly stats.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadWeekly();
  }, [loadWeekly]);

  useEffect(() => {
    if (!exerciseId) {
      setPr(null);
      setRec(null);
      setPrSeries(null);
      return;
    }
    let cancelled = false;
    setDetailLoading(true);
    setDetailError(null);
    Promise.allSettled([
      statsApi.personalRecord(exerciseId),
      statsApi.nextSessionRecommendation(exerciseId),
      statsApi.prHistory(exerciseId),
    ]).then((results) => {
      if (cancelled) return;
      const [prResult, recResult, historyResult] = results;
      setPr(prResult.status === "fulfilled" ? prResult.value : null);
      setRec(recResult.status === "fulfilled" ? recResult.value : null);
      setPrSeries(historyResult.status === "fulfilled" ? historyResult.value : null);
      if (prResult.status === "rejected" && recResult.status === "rejected") {
        const reason = prResult.reason;
        setDetailError(
          reason instanceof ApiError ? reason.message : "No history for this exercise yet.",
        );
      }
      setDetailLoading(false);
    });
    return () => {
      cancelled = true;
    };
  }, [exerciseId]);

  const trendData = useMemo(
    () =>
      trend?.points.map((p) => ({
        label: formatDate(p.weekStart).slice(0, 6),
        volume: toNum(p.totalVolume),
      })) ?? [],
    [trend],
  );

  const categoryData = useMemo(() => {
    const rows =
      weekly?.volumeByCategory.map((row) => ({
        category: row.category,
        volume: toNum(row.volume),
      })) ?? [];
    const max = Math.max(1, ...rows.map((r) => r.volume));
    return rows
      .slice()
      .sort((a, b) => b.volume - a.volume)
      .map((r) => ({ ...r, pct: (r.volume / max) * 100 }));
  }, [weekly]);

  const oneRmData =
    prSeries?.points.map((p) => ({
      label: formatDate(p.at).slice(0, 6),
      e1rm: toNum(p.estimatedOneRepMax),
    })) ?? [];

  if (loading) {
    return <LoadingBlock label="Loading stats…" />;
  }

  const emptyWeek =
    !weekly ||
    (weekly.completedWorkouts === 0 &&
      toNum(weekly.totalVolume) === 0 &&
      weekly.totalSets === 0);

  return (
    <div className="stack">
      <PageHeader
        title="Stats"
        subtitle={
          weekly
            ? `${formatDate(weekly.weekStart)} – ${formatDate(weekly.weekEnd)}`
            : "Weekly training summary"
        }
      />

      {error ? <ErrorBanner message={error} onRetry={() => void loadWeekly()} /> : null}

      {emptyWeek ? (
        <div className="card card-feature card-accent-progress stack" style={{ padding: "1.75rem" }}>
          <EmptyState
            title="No training volume yet"
            description="Complete and confirm workouts to unlock weekly charts, category bars, and PR trends."
            action={
              <div className="row">
                <Link to="/session" className="btn btn-primary">
                  Start a session
                </Link>
                <Link to="/workouts" className="btn btn-secondary">
                  Workout history
                </Link>
              </div>
            }
          />
        </div>
      ) : null}

      {weekly && !emptyWeek ? (
        <>
          <section className="grid-3">
            <MetricCard
              label="Completed workouts"
              accent="train"
              value={weekly.completedWorkouts}
            />
            <MetricCard
              label="Total volume"
              accent="progress"
              value={formatNumber(weekly.totalVolume, 0)}
              hint={`${weekly.totalSets} sets · ${weekly.totalReps} reps`}
            />
            <MetricCard
              label="vs prior week"
              accent="caution"
              value={
                weekly.comparisonWithPriorWeek.volumeChangePercent == null
                  ? "—"
                  : `${formatNumber(weekly.comparisonWithPriorWeek.volumeChangePercent, 1)}%`
              }
              hint={`Δ ${formatNumber(weekly.comparisonWithPriorWeek.volumeChange, 0)} volume`}
            />
          </section>

          <section className="card stack">
            <h2>Volume trend</h2>
            {trendData.every((d) => d.volume === 0) ? (
              <EmptyState
                title="No volume trend yet"
                description="Complete workouts across multiple weeks to populate this chart."
              />
            ) : (
              <div style={{ width: "100%", height: 320 }}>
                <ResponsiveContainer>
                  <LineChart data={trendData}>
                    <CartesianGrid stroke="var(--color-border)" strokeDasharray="3 3" />
                    <XAxis dataKey="label" stroke="var(--color-text-muted)" fontSize={12} />
                    <YAxis stroke="var(--color-text-muted)" fontSize={12} />
                    <Tooltip
                      contentStyle={{
                        background: "var(--color-surface-raised)",
                        border: "1px solid var(--color-border)",
                      }}
                    />
                    <Line
                      type="monotone"
                      dataKey="volume"
                      stroke="var(--color-train)"
                      strokeWidth={2.5}
                      dot={false}
                    />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            )}
          </section>

          <section className="grid-2">
            <div className="card stack">
              <h2>Volume by category</h2>
              {categoryData.length === 0 ? (
                <EmptyState title="No category volume" />
              ) : (
                <div className="stack" style={{ gap: "0.75rem" }}>
                  {categoryData.map((row) => (
                    <div key={row.category} className="category-bar">
                      <span className="muted">{row.category.replaceAll("_", " ")}</span>
                      <div className="category-bar-track">
                        <div className="category-bar-fill" style={{ width: `${row.pct}%` }} />
                      </div>
                      <span className="mono">{formatNumber(row.volume, 0)}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="card stack">
              <h2>Volume by exercise</h2>
              {weekly.volumeByExercise.length === 0 ? (
                <EmptyState
                  title="No volume this week"
                  description="Confirm completed workouts to populate this."
                />
              ) : (
                <div className="stack" style={{ gap: "0.55rem" }}>
                  {weekly.volumeByExercise.map((row) => (
                    <div key={row.exerciseId} className="set-card">
                      <Link to={`/exercises/${row.exerciseId}`} style={{ fontWeight: 650 }}>
                        {row.exerciseName}
                      </Link>
                      <span className="mono">{formatNumber(row.volume, 0)}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </section>
        </>
      ) : null}

      <section className="card stack">
        <h2>Personal records</h2>
        <div className="field">
          <label htmlFor="exercise-select">Exercise</label>
          <select
            id="exercise-select"
            value={exerciseId}
            onChange={(e) => setExerciseId(e.target.value)}
          >
            {exercises.map((exercise) => (
              <option key={exercise.id} value={exercise.id}>
                {exercise.name}
              </option>
            ))}
          </select>
        </div>

        {detailLoading ? <LoadingBlock label="Loading exercise stats…" /> : null}
        {detailError && !pr ? <EmptyState title={detailError} /> : null}

        {pr ? (
          <div className="grid-2">
            <MetricCard
              label="Heaviest"
              accent="train"
              value={`${formatNumber(pr.heaviestWeight, 1)} × ${pr.repsAtHeaviestWeight}`}
              hint={formatDate(pr.heaviestWeightAchievedAt)}
            />
            <MetricCard
              label="Est. 1RM (Epley)"
              accent="caution"
              value={formatNumber(pr.estimatedOneRepMax, 1)}
              hint={pr.disclaimer}
            />
          </div>
        ) : null}

        {oneRmData.length > 0 ? (
          <div style={{ width: "100%", height: 260 }}>
            <ResponsiveContainer>
              <LineChart data={oneRmData}>
                <CartesianGrid stroke="var(--color-border)" strokeDasharray="3 3" />
                <XAxis dataKey="label" stroke="var(--color-text-muted)" fontSize={12} />
                <YAxis stroke="var(--color-text-muted)" fontSize={12} />
                <Tooltip
                  contentStyle={{
                    background: "var(--color-surface-raised)",
                    border: "1px solid var(--color-border)",
                  }}
                />
                <Line
                  type="monotone"
                  dataKey="e1rm"
                  stroke="var(--color-caution)"
                  strokeWidth={2}
                  dot
                />
              </LineChart>
            </ResponsiveContainer>
          </div>
        ) : null}

        {rec ? (
          <div className="stack">
            <p style={{ margin: 0 }}>{rec.reason}</p>
            <div className="grid-2">
              <div className="card card-accent-train" style={{ padding: "1rem" }}>
                <div className="muted">Option A</div>
                <div className="mono" style={{ fontSize: "1.25rem", fontWeight: 700 }}>
                  {formatNumber(rec.recommendation.optionA.weight, 1)} ×{" "}
                  {rec.recommendation.optionA.targetReps}
                </div>
              </div>
              <div className="card card-accent-progress" style={{ padding: "1rem" }}>
                <div className="muted">Option B</div>
                <div className="mono" style={{ fontSize: "1.25rem", fontWeight: 700 }}>
                  {formatNumber(rec.recommendation.optionB.weight, 1)} ×{" "}
                  {rec.recommendation.optionB.targetReps}
                </div>
              </div>
            </div>
            <p className="muted" style={{ margin: 0, fontSize: "var(--text-sm)" }}>
              {rec.disclaimer}
            </p>
          </div>
        ) : null}
      </section>
    </div>
  );
}
