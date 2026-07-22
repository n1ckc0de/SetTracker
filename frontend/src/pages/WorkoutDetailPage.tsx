import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ApiError } from "../api/http";
import * as workoutsApi from "../api/workoutsApi";
import type { WorkoutDetail } from "../api/types";
import { useToast } from "../components/Toast";
import {
  EmptyState,
  ErrorBanner,
  LoadingBlock,
  MetricCard,
  PageHeader,
  StatusBadge,
  formatDateTime,
  formatDuration,
  formatNumber,
} from "../components/ui";

function toNum(value: number | string): number {
  return typeof value === "number" ? value : Number(value);
}

export function WorkoutDetailPage() {
  const { workoutId = "" } = useParams();
  const { push } = useToast();
  const [workout, setWorkout] = useState<WorkoutDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setWorkout(await workoutsApi.getWorkout(workoutId));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Workout not found.");
      setWorkout(null);
    } finally {
      setLoading(false);
    }
  }, [workoutId]);

  useEffect(() => {
    void load();
  }, [load]);

  const volume = useMemo(() => {
    if (!workout) return 0;
    return workout.sets.reduce((sum, s) => sum + toNum(s.weight) * s.reps, 0);
  }, [workout]);

  async function complete() {
    setBusy(true);
    try {
      setWorkout(await workoutsApi.completeWorkout(workoutId));
      push("Workout completed", "success");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not complete workout.");
    } finally {
      setBusy(false);
    }
  }

  async function cancel() {
    setBusy(true);
    try {
      await workoutsApi.cancelWorkout(workoutId);
      push("Workout cancelled", "success");
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not cancel workout.");
    } finally {
      setBusy(false);
    }
  }

  if (loading) {
    return <LoadingBlock label="Loading workout…" />;
  }

  if (!workout) {
    return (
      <div className="stack">
        {error ? (
          <ErrorBanner message={error} onRetry={() => void load()} backTo="/workouts" />
        ) : null}
        <EmptyState
          title="Workout unavailable"
          action={
            <Link className="btn btn-secondary" to="/workouts">
              Back to workouts
            </Link>
          }
        />
      </div>
    );
  }

  return (
    <div className="stack">
      <Link to="/workouts" className="muted" style={{ textDecoration: "none", width: "fit-content" }}>
        ← Workouts
      </Link>

      <PageHeader
        title="Workout detail"
        subtitle={formatDateTime(workout.startedAt)}
        actions={
          workout.status === "ACTIVE" ? (
            <div className="row">
              <Link className="btn btn-primary" to="/session">
                Open live session
              </Link>
              <button type="button" className="btn btn-secondary" disabled={busy} onClick={() => void complete()}>
                Complete
              </button>
              <button type="button" className="btn btn-danger" disabled={busy} onClick={() => void cancel()}>
                Cancel
              </button>
            </div>
          ) : undefined
        }
      />

      {error ? <ErrorBanner message={error} /> : null}

      <section className="grid-3">
        <MetricCard
          label="Duration"
          accent="train"
          value={formatDuration(workout.startedAt, workout.endedAt)}
        />
        <MetricCard label="Volume" accent="progress" value={formatNumber(volume, 0)} hint={`${workout.sets.length} sets`} />
        <MetricCard
          label="Status"
          accent="caution"
          value={
            <div className="row" style={{ marginTop: "0.35rem" }}>
              <StatusBadge status={workout.status} />
              <span className="muted" style={{ fontSize: "var(--text-sm)" }}>
                {workout.source.replaceAll("_", " ")}
              </span>
              {workout.importStatus !== "NOT_APPLICABLE" ? (
                <StatusBadge status={workout.importStatus} />
              ) : null}
            </div>
          }
        />
      </section>

      <div className="card stack">
        <h2>Sets</h2>
        {workout.sets.length === 0 ? (
          <EmptyState
            title="No sets logged"
            description="Add sets from the Active Session view."
            action={
              workout.status === "ACTIVE" ? (
                <Link className="btn btn-primary" to="/session">
                  Open session
                </Link>
              ) : undefined
            }
          />
        ) : (
          <div className="stack" style={{ gap: "0.55rem" }}>
            {workout.sets.map((set) => (
              <div key={set.id} className="set-card">
                <div>
                  <div style={{ fontWeight: 650 }}>
                    {set.exerciseName} · Set {set.setNumber}
                  </div>
                  <div className="muted" style={{ fontSize: "var(--text-sm)" }}>
                    {set.exerciseCategory.replaceAll("_", " ")} · {formatDateTime(set.loggedAt)}
                  </div>
                </div>
                <div className="mono" style={{ fontSize: "1.15rem", fontWeight: 700 }}>
                  {formatNumber(set.weight, 1)} × {set.reps}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
