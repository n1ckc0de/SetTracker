import { useCallback, useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ApiError } from "../api/http";
import * as workoutsApi from "../api/workoutsApi";
import type { Workout, WorkoutSource, WorkoutStatus } from "../api/types";
import { useToast } from "../components/Toast";
import {
  EmptyState,
  ErrorBanner,
  LoadingBlock,
  PageHeader,
  StatusBadge,
  formatDateTime,
  formatDuration,
} from "../components/ui";

export function WorkoutsPage() {
  const navigate = useNavigate();
  const { push } = useToast();
  const [status, setStatus] = useState<WorkoutStatus | "">("");
  const [source, setSource] = useState<WorkoutSource | "">("");
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [items, setItems] = useState<Workout[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [starting, setStarting] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await workoutsApi.listWorkouts({
        status,
        source,
        page,
        size: 10,
      });
      setItems(result.content);
      setTotalPages(result.totalPages);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to load workouts.");
    } finally {
      setLoading(false);
    }
  }, [status, source, page]);

  useEffect(() => {
    void load();
  }, [load]);

  async function startWorkout() {
    setStarting(true);
    setError(null);
    try {
      const workout = await workoutsApi.startWorkout();
      push("Workout started", "success");
      navigate("/session");
      void workout;
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not start workout.");
    } finally {
      setStarting(false);
    }
  }

  return (
    <div className="stack">
      <PageHeader
        title="Workouts"
        subtitle="History with filters. Soft-cancel preserves audit history."
        actions={
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => void startWorkout()}
            disabled={starting}
          >
            {starting ? "Starting…" : "Start workout"}
          </button>
        }
      />

      <div className="card row">
        <div className="field">
          <label htmlFor="status">Status</label>
          <select
            id="status"
            value={status}
            onChange={(e) => {
              setPage(0);
              setStatus(e.target.value as WorkoutStatus | "");
            }}
          >
            <option value="">All</option>
            <option value="ACTIVE">ACTIVE</option>
            <option value="COMPLETED">COMPLETED</option>
            <option value="CANCELLED">CANCELLED</option>
          </select>
        </div>
        <div className="field">
          <label htmlFor="source">Source</label>
          <select
            id="source"
            value={source}
            onChange={(e) => {
              setPage(0);
              setSource(e.target.value as WorkoutSource | "");
            }}
          >
            <option value="">All</option>
            <option value="MANUAL">MANUAL</option>
            <option value="WEBHOOK">WEBHOOK</option>
            <option value="NOTES_IMPORT">NOTES_IMPORT</option>
          </select>
        </div>
      </div>

      {error ? <ErrorBanner message={error} onRetry={() => void load()} /> : null}
      {loading ? <LoadingBlock /> : null}

      {!loading && items.length === 0 ? (
        <EmptyState
          title="No workouts match these filters"
          description="Try clearing filters or start a new session."
          action={
            <button type="button" className="btn btn-primary" onClick={() => void startWorkout()} disabled={starting}>
              Start workout
            </button>
          }
        />
      ) : null}

      {!loading && items.length > 0 ? (
        <>
          <div className="card-grid">
            {items.map((workout) => (
              <Link
                key={workout.id}
                to={`/workouts/${workout.id}`}
                className="card card-link stack"
                style={{ gap: "0.65rem" }}
              >
                <div className="row" style={{ justifyContent: "space-between" }}>
                  <StatusBadge status={workout.status} />
                  <span className="muted" style={{ fontSize: "var(--text-sm)" }}>
                    {workout.source.replaceAll("_", " ")}
                  </span>
                </div>
                <div className="mono" style={{ fontFamily: "var(--font-display)", fontSize: "1.15rem", fontWeight: 700 }}>
                  {formatDateTime(workout.startedAt)}
                </div>
                <div className="muted" style={{ fontSize: "var(--text-sm)" }}>
                  Duration {formatDuration(workout.startedAt, workout.endedAt)}
                  {workout.importStatus !== "NOT_APPLICABLE"
                    ? ` · Import ${workout.importStatus.replaceAll("_", " ")}`
                    : ""}
                </div>
              </Link>
            ))}
          </div>
          <div className="row" style={{ justifyContent: "space-between" }}>
            <button
              type="button"
              className="btn btn-secondary"
              disabled={page <= 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              Previous
            </button>
            <span className="muted mono">
              Page {page + 1} / {Math.max(totalPages, 1)}
            </span>
            <button
              type="button"
              className="btn btn-secondary"
              disabled={page + 1 >= totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </button>
          </div>
        </>
      ) : null}
    </div>
  );
}
