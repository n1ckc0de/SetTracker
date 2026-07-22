import { useCallback, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
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
import * as exercisesApi from "../api/exercisesApi";
import type { ExerciseDetail, ExerciseMediaSuggestion } from "../api/types";
import { ExerciseThumbnail, YouTubeEmbed } from "../components/ExerciseMedia";
import { useToast } from "../components/Toast";
import {
  EmptyState,
  ErrorBanner,
  LoadingBlock,
  MetricCard,
  PageHeader,
  StatusBadge,
  formatDate,
  formatDateTime,
  formatNumber,
} from "../components/ui";

function toNum(value: number | string): number {
  return typeof value === "number" ? value : Number(value);
}

export function ExerciseDetailPage() {
  const { exerciseId = "" } = useParams();
  const { push } = useToast();
  const [detail, setDetail] = useState<ExerciseDetail | null>(null);
  const [suggestions, setSuggestions] = useState<ExerciseMediaSuggestion[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [generating, setGenerating] = useState(false);
  const [suggesting, setSuggesting] = useState(false);
  const [reviewingId, setReviewingId] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!exerciseId) return;
    setLoading(true);
    setError(null);
    try {
      const [nextDetail, pending] = await Promise.all([
        exercisesApi.getExerciseDetail(exerciseId),
        exercisesApi.listMediaSuggestions(exerciseId, "PENDING"),
      ]);
      setDetail(nextDetail);
      setSuggestions(pending);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to load exercise detail.");
      setDetail(null);
      setSuggestions([]);
    } finally {
      setLoading(false);
    }
  }, [exerciseId]);

  useEffect(() => {
    void load();
  }, [load]);

  async function onGenerateCoach() {
    if (!exerciseId) return;
    setGenerating(true);
    setError(null);
    try {
      await coachApi.generateRecommendation(exerciseId);
      push("Coach recommendation generated", "success");
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not generate recommendation.");
    } finally {
      setGenerating(false);
    }
  }

  async function onSuggestMedia() {
    if (!exerciseId) return;
    setSuggesting(true);
    setError(null);
    try {
      const created = await exercisesApi.generateMediaSuggestions(exerciseId);
      setSuggestions(created.filter((s) => s.status === "PENDING"));
      push(
        created.length > 0
          ? `${created.length} media suggestion${created.length === 1 ? "" : "s"} ready for review`
          : "No media suggestions found for this exercise",
        created.length > 0 ? "success" : "default",
      );
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not suggest media.");
    } finally {
      setSuggesting(false);
    }
  }

  async function onConfirmSuggestion(suggestionId: string) {
    if (!exerciseId) return;
    setReviewingId(suggestionId);
    setError(null);
    try {
      await exercisesApi.confirmMediaSuggestion(exerciseId, suggestionId);
      push("Media published to exercise", "success");
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not confirm suggestion.");
    } finally {
      setReviewingId(null);
    }
  }

  async function onRejectSuggestion(suggestionId: string) {
    if (!exerciseId) return;
    setReviewingId(suggestionId);
    setError(null);
    try {
      await exercisesApi.rejectMediaSuggestion(exerciseId, suggestionId);
      push("Suggestion rejected", "success");
      setSuggestions((prev) => prev.filter((s) => s.id !== suggestionId));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not reject suggestion.");
    } finally {
      setReviewingId(null);
    }
  }

  if (loading) {
    return <LoadingBlock label="Loading exercise…" />;
  }

  if (error && !detail) {
    return (
      <div className="stack">
        <PageHeader title="Exercise" subtitle="Could not load this exercise." />
        <ErrorBanner
          message={error}
          onRetry={() => void load()}
          backTo="/exercises"
          backLabel="Back to exercises"
        />
      </div>
    );
  }

  if (!detail) {
    return (
      <EmptyState
        title="Exercise not found"
        action={
          <Link to="/exercises" className="btn btn-secondary">
            Back to exercises
          </Link>
        }
      />
    );
  }

  const points = detail.prHistory?.points ?? [];
  const chartData = points.map((p) => ({
    label: formatDate(p.at).slice(0, 6),
    e1rm: toNum(p.estimatedOneRepMax),
  }));
  const exercise = detail.exercise;
  const canConfirmSuggestion =
    exercise.owned ||
    (exercise.global &&
      !exercise.thumbnailUrl &&
      !exercise.demoVideoUrl &&
      !exercise.mediaAttribution);

  return (
    <div className="stack">
      <Link to="/exercises" className="muted" style={{ textDecoration: "none", width: "fit-content" }}>
        ← Exercises
      </Link>

      <PageHeader
        title={exercise.name}
        subtitle={`${exercise.category}${exercise.global ? " · Global" : " · Custom"}`}
        actions={
          <div className="row" style={{ gap: "0.5rem", flexWrap: "wrap" }}>
            <button
              type="button"
              className="btn btn-secondary"
              disabled={suggesting}
              onClick={() => void onSuggestMedia()}
            >
              {suggesting ? "Suggesting…" : "Suggest media"}
            </button>
            <Link to="/strength" className="btn btn-ghost">
              1RM calculator
            </Link>
            <button
              type="button"
              className="btn btn-coach"
              disabled={generating}
              onClick={() => void onGenerateCoach()}
            >
              {generating ? "Generating…" : "Generate coach tip"}
            </button>
          </div>
        }
      />

      {error ? <ErrorBanner message={error} onRetry={() => void load()} backTo="/exercises" /> : null}

      <section className="grid-2">
        <div className="stack">
          <ExerciseThumbnail url={exercise.thumbnailUrl} name={exercise.name} category={exercise.category} />
          {exercise.demoVideoUrl ? (
            <YouTubeEmbed
              urlOrId={exercise.demoVideoUrl}
              title={`${exercise.name} demonstration`}
              attribution={exercise.mediaAttribution}
            />
          ) : null}
        </div>

        <div className="stack">
          {detail.personalRecord ? (
            <div className="grid-2">
              <MetricCard
                label="Heaviest"
                accent="train"
                value={`${formatNumber(detail.personalRecord.heaviestWeight, 1)} × ${detail.personalRecord.repsAtHeaviestWeight}`}
                hint={`Set on ${formatDate(detail.personalRecord.heaviestWeightAchievedAt)}`}
              />
              <MetricCard
                label="Est. 1RM"
                accent="caution"
                value={formatNumber(detail.personalRecord.estimatedOneRepMax, 1)}
                hint={detail.personalRecord.disclaimer}
              />
            </div>
          ) : (
            <EmptyState
              title="No PRs yet"
              description="Log confirmed sets to unlock strength stats for this movement."
            />
          )}

          {detail.activeRecommendation ? (
            <div className="card card-feature card-accent-coach stack">
              <div className="row" style={{ justifyContent: "space-between" }}>
                <h2>Coach</h2>
                <StatusBadge status={detail.activeRecommendation.status} />
              </div>
              <div className="row">
                <span className="badge badge-coach">
                  {detail.activeRecommendation.recommendationType.replaceAll("_", " ")}
                </span>
                <span className="badge">{detail.activeRecommendation.confidenceLevel} confidence</span>
              </div>
              <p style={{ margin: 0 }}>{detail.activeRecommendation.reasoning}</p>
              <pre
                className="mono"
                style={{
                  margin: 0,
                  whiteSpace: "pre-wrap",
                  fontSize: "var(--text-sm)",
                  background: "var(--color-ink-elevated)",
                  padding: "0.75rem",
                  borderRadius: "var(--radius-sm)",
                }}
              >
                {detail.activeRecommendation.proposedChange}
              </pre>
              <Link className="btn btn-coach" to="/coach">
                Manage on Coach page
              </Link>
            </div>
          ) : (
            <div className="card card-feature card-accent-coach stack">
              <h2>Coach</h2>
              <EmptyState
                title="No proposed recommendation"
                description="Generate one for this exercise based on recent performance."
                action={
                  <button
                    type="button"
                    className="btn btn-coach"
                    disabled={generating}
                    onClick={() => void onGenerateCoach()}
                  >
                    {generating ? "Generating…" : "Generate recommendation"}
                  </button>
                }
              />
            </div>
          )}
        </div>
      </section>

      <section className="card stack">
        <div className="row" style={{ justifyContent: "space-between", alignItems: "baseline" }}>
          <h2 style={{ margin: 0 }}>Pending media suggestions</h2>
          <span className="muted" style={{ fontSize: "var(--text-sm)" }}>
            Review before publishing
          </span>
        </div>
        {suggestions.length === 0 ? (
          <EmptyState
            title="No pending suggestions"
            description="Use Suggest media to look up curated demos and Wikimedia images."
            action={
              <button
                type="button"
                className="btn btn-secondary"
                disabled={suggesting}
                onClick={() => void onSuggestMedia()}
              >
                {suggesting ? "Suggesting…" : "Suggest media"}
              </button>
            }
          />
        ) : (
          <div className="stack" style={{ gap: "1rem" }}>
            {suggestions.map((suggestion) => (
              <div key={suggestion.id} className="stack" style={{ gap: "0.65rem" }}>
                <div
                  className="row"
                  style={{ justifyContent: "space-between", flexWrap: "wrap", gap: "0.5rem" }}
                >
                  <div className="row" style={{ gap: "0.4rem", flexWrap: "wrap" }}>
                    <span className="badge">{suggestion.source.replaceAll("_", " ")}</span>
                    <span className="badge">{suggestion.confidence} confidence</span>
                    <StatusBadge status={suggestion.status} />
                  </div>
                  <div className="row" style={{ gap: "0.4rem" }}>
                    {canConfirmSuggestion ? (
                      <button
                        type="button"
                        className="btn btn-primary"
                        disabled={reviewingId === suggestion.id}
                        onClick={() => void onConfirmSuggestion(suggestion.id)}
                      >
                        {reviewingId === suggestion.id ? "Saving…" : "Confirm"}
                      </button>
                    ) : null}
                    <button
                      type="button"
                      className="btn btn-ghost"
                      disabled={reviewingId === suggestion.id}
                      onClick={() => void onRejectSuggestion(suggestion.id)}
                    >
                      Reject
                    </button>
                  </div>
                </div>
                {suggestion.thumbnailUrl ? (
                  <ExerciseThumbnail
                    url={suggestion.thumbnailUrl}
                    name={exercise.name}
                    category={exercise.category}
                  />
                ) : null}
                {suggestion.demoVideoUrl ? (
                  <YouTubeEmbed
                    urlOrId={suggestion.demoVideoUrl}
                    title={`${exercise.name} suggested demonstration`}
                    attribution={suggestion.mediaAttribution}
                  />
                ) : (
                  <p className="muted" style={{ margin: 0, fontSize: "var(--text-sm)" }}>
                    {suggestion.mediaAttribution}
                  </p>
                )}
              </div>
            ))}
          </div>
        )}
        {!canConfirmSuggestion && suggestions.length > 0 ? (
          <p className="muted" style={{ margin: 0, fontSize: "var(--text-sm)" }}>
            Confirm is unavailable because this global exercise already has published media (or you
            do not own this custom exercise).
          </p>
        ) : null}
      </section>

      <section className="card stack">
        <h2>Strength progression</h2>
        {chartData.length === 0 ? (
          <EmptyState
            title="No 1RM history"
            description="Estimated one-rep max points appear after confirmed sets."
          />
        ) : (
          <div style={{ width: "100%", height: 280 }}>
            <ResponsiveContainer>
              <LineChart data={chartData}>
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
        )}
      </section>

      <section className="card stack">
        <h2>Recent sets</h2>
        {detail.recentSets.length === 0 ? (
          <EmptyState title="No recent sets" description="Sets from confirmed workouts will show here." />
        ) : (
          <div className="stack" style={{ gap: "0.55rem" }}>
            {detail.recentSets.map((row) => (
              <div key={row.setId} className="set-card">
                <div>
                  <div style={{ fontWeight: 650 }}>Set {row.setNumber}</div>
                  <div className="muted" style={{ fontSize: "var(--text-sm)" }}>
                    {formatDateTime(row.loggedAt)}
                  </div>
                </div>
                <div className="mono" style={{ fontSize: "1.1rem", fontWeight: 700 }}>
                  {formatNumber(row.weight, 1)} × {row.reps}
                </div>
                <Link to={`/workouts/${row.workoutId}`} className="btn btn-ghost" style={{ minHeight: 36 }}>
                  Workout
                </Link>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
