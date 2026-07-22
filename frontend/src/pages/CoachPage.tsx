import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import { ApiError } from "../api/http";
import * as coachApi from "../api/coachApi";
import { listExercises } from "../api/exercisesApi";
import type {
  CoachRecommendation,
  Exercise,
  RecommendationStatus,
} from "../api/types";
import { Modal } from "../components/Modal";
import { RecommendationCard } from "../components/RecommendationCard";
import { useToast } from "../components/Toast";
import {
  EmptyState,
  ErrorBanner,
  LoadingBlock,
  PageHeader,
} from "../components/ui";

export function CoachPage() {
  const { push } = useToast();
  const [exercises, setExercises] = useState<Exercise[]>([]);
  const [exerciseId, setExerciseId] = useState("");
  const [recs, setRecs] = useState<CoachRecommendation[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<RecommendationStatus | "">("PROPOSED");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [chat, setChat] = useState("");
  const [modifyId, setModifyId] = useState<string | null>(null);
  const [modifyChange, setModifyChange] = useState("");
  const [modifyReason, setModifyReason] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [exercisePage, recommendations] = await Promise.all([
        listExercises({ size: 100, sort: "name,asc" }),
        coachApi.listRecommendations(statusFilter || undefined),
      ]);
      setExercises(exercisePage.content);
      setExerciseId((current) => current || exercisePage.content[0]?.id || "");
      setRecs(recommendations);
      setSelectedId((current) => {
        if (current && recommendations.some((r) => r.id === current)) return current;
        return recommendations[0]?.id ?? null;
      });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to load coach data.");
    } finally {
      setLoading(false);
    }
  }, [statusFilter]);

  useEffect(() => {
    void load();
  }, [load]);

  const selected = useMemo(
    () => recs.find((r) => r.id === selectedId) ?? null,
    [recs, selectedId],
  );

  const exerciseName = (id: string | null) =>
    id ? exercises.find((e) => e.id === id)?.name : undefined;

  async function onGenerate() {
    setBusy(true);
    setError(null);
    try {
      const created = await coachApi.generateRecommendation(exerciseId || undefined);
      setStatusFilter("PROPOSED");
      setSelectedId(created.id);
      push("Recommendation generated", "success");
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not generate recommendation.");
    } finally {
      setBusy(false);
    }
  }

  async function onChat(event: FormEvent) {
    event.preventDefault();
    if (!chat.trim()) return;
    setBusy(true);
    setError(null);
    try {
      const created = await coachApi.coachChat(chat.trim(), exerciseId || undefined);
      setChat("");
      setStatusFilter("PROPOSED");
      setSelectedId(created.id);
      push("Coach reply saved as recommendation", "success");
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Coach chat failed.");
    } finally {
      setBusy(false);
    }
  }

  async function onAccept(id: string) {
    setBusy(true);
    try {
      await coachApi.acceptRecommendation(id);
      push("Accepted — workouts are not auto-updated", "success");
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Accept failed.");
    } finally {
      setBusy(false);
    }
  }

  async function onDismiss(id: string) {
    setBusy(true);
    try {
      await coachApi.dismissRecommendation(id);
      push("Recommendation dismissed", "success");
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Dismiss failed.");
    } finally {
      setBusy(false);
    }
  }

  async function onModify(event: FormEvent) {
    event.preventDefault();
    if (!modifyId) return;
    setBusy(true);
    try {
      await coachApi.modifyRecommendation(modifyId, modifyChange, modifyReason || undefined);
      setModifyId(null);
      push("Recommendation modified", "success");
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Modify failed.");
    } finally {
      setBusy(false);
    }
  }

  if (loading) {
    return <LoadingBlock label="Loading coach…" />;
  }

  return (
    <div className="stack">
      <PageHeader
        title="AI Coach"
        subtitle="Bounds-checked recommendations — you decide what to apply."
      />

      <div className="card card-feature card-accent-coach">
        <p style={{ margin: 0 }}>
          Accepting a recommendation records your decision only. It does{" "}
          <strong>not</strong> automatically mutate workouts, programs, or logged sets — apply
          changes yourself in the next session.
        </p>
      </div>

      {error ? <ErrorBanner message={error} onRetry={() => void load()} /> : null}

      <div className="coach-layout">
        <aside className="card stack">
          <h2>Generate</h2>
          <div className="field">
            <label htmlFor="coach-ex">Exercise</label>
            <select
              id="coach-ex"
              value={exerciseId}
              onChange={(e) => setExerciseId(e.target.value)}
            >
              {exercises.map((ex) => (
                <option key={ex.id} value={ex.id}>
                  {ex.name}
                </option>
              ))}
            </select>
          </div>
          <button
            type="button"
            className="btn btn-coach"
            disabled={busy || !exerciseId}
            onClick={() => void onGenerate()}
          >
            Generate recommendation
          </button>

          <h3 style={{ marginTop: "0.5rem" }}>History</h3>
          <div className="row" style={{ gap: "0.35rem", flexWrap: "wrap" }}>
            {(
              [
                ["PROPOSED", "Proposed"],
                ["ACCEPTED", "Accepted"],
                ["MODIFIED", "Modified"],
                ["DISMISSED", "Dismissed"],
                ["", "All"],
              ] as const
            ).map(([value, label]) => (
              <button
                key={label}
                type="button"
                className={`chip${statusFilter === value ? " active" : ""}`}
                onClick={() => setStatusFilter(value)}
              >
                {label}
              </button>
            ))}
          </div>
          {recs.length === 0 ? (
            <EmptyState title="No cards yet" description="Generate or chat to create one." />
          ) : (
            <div className="stack" style={{ gap: "0.45rem" }}>
              {recs.map((rec) => (
                <button
                  key={rec.id}
                  type="button"
                  className={`set-card${selectedId === rec.id ? " active-rec" : ""}`}
                  style={{
                    textAlign: "left",
                    cursor: "pointer",
                    borderColor:
                      selectedId === rec.id ? "var(--color-coach)" : undefined,
                    width: "100%",
                  }}
                  onClick={() => setSelectedId(rec.id)}
                >
                  <div>
                    <div style={{ fontWeight: 650, fontSize: "var(--text-sm)" }}>
                      {rec.recommendationType.replaceAll("_", " ")}
                    </div>
                    <div className="muted" style={{ fontSize: "var(--text-xs)" }}>
                      {exerciseName(rec.exerciseId) ?? "General"} · {rec.status}
                    </div>
                  </div>
                </button>
              ))}
            </div>
          )}
        </aside>

        <section className="stack">
          {selected ? (
            <RecommendationCard
              recommendation={selected}
              exerciseName={exerciseName(selected.exerciseId)}
              actions={
                selected.status === "PROPOSED" ? (
                  <div className="row">
                    <button
                      type="button"
                      className="btn btn-coach"
                      disabled={busy}
                      onClick={() => void onAccept(selected.id)}
                    >
                      Accept
                    </button>
                    <button
                      type="button"
                      className="btn btn-secondary"
                      disabled={busy}
                      onClick={() => {
                        setModifyId(selected.id);
                        setModifyChange(selected.proposedChange);
                        setModifyReason(selected.reasoning);
                      }}
                    >
                      Modify
                    </button>
                    <button
                      type="button"
                      className="btn btn-ghost"
                      disabled={busy}
                      onClick={() => void onDismiss(selected.id)}
                    >
                      Dismiss
                    </button>
                  </div>
                ) : undefined
              }
            />
          ) : (
            <EmptyState
              title="Select a recommendation"
              description="Pick from the history list or generate a new card."
            />
          )}
        </section>

        <aside className="card stack">
          <h2>Chat check-in</h2>
          <p className="muted" style={{ margin: 0, fontSize: "var(--text-sm)" }}>
            Free text becomes a readiness note + structured recommendation (not a freeform reply).
            Try: “I only slept five hours” or “My shoulders feel sore.”
          </p>
          <form className="stack" onSubmit={onChat}>
            <div className="field">
              <label htmlFor="chat">Message</label>
              <textarea
                id="chat"
                value={chat}
                onChange={(e) => setChat(e.target.value)}
                rows={5}
                maxLength={2000}
                placeholder="How are you feeling today?"
                required
              />
            </div>
            <button type="submit" className="btn btn-secondary" disabled={busy}>
              Send
            </button>
          </form>
          {selected ? (
            <div>
              <h3>Selected proposal</h3>
              <pre className="mono rec-pre">{selected.proposedChange}</pre>
              <p className="muted" style={{ fontSize: "var(--text-xs)", margin: "0.5rem 0 0" }}>
                Server clamps weight/rep changes to safe bounds. Invalid values are rejected.
              </p>
            </div>
          ) : null}
        </aside>
      </div>

      <Modal
        open={!!modifyId}
        title="Modify recommendation"
        onClose={() => setModifyId(null)}
        footer={
          <>
            <button type="submit" form="modify-rec-form" className="btn btn-coach" disabled={busy}>
              Save modification
            </button>
            <button type="button" className="btn btn-ghost" onClick={() => setModifyId(null)}>
              Cancel
            </button>
          </>
        }
      >
        <form id="modify-rec-form" className="stack" onSubmit={onModify}>
          <div className="field">
            <label htmlFor="mod-change">Proposed change (JSON)</label>
            <textarea
              id="mod-change"
              className="mono"
              value={modifyChange}
              onChange={(e) => setModifyChange(e.target.value)}
              rows={4}
              required
            />
            <span className="muted" style={{ fontSize: "var(--text-xs)" }}>
              Keep keys the coach expects (e.g. weightDelta). Out-of-bound values are clamped or rejected server-side.
            </span>
          </div>
          <div className="field">
            <label htmlFor="mod-reason">Reasoning</label>
            <textarea
              id="mod-reason"
              value={modifyReason}
              onChange={(e) => setModifyReason(e.target.value)}
              rows={3}
            />
          </div>
        </form>
      </Modal>
    </div>
  );
}
