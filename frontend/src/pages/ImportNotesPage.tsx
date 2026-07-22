import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { ApiError } from "../api/http";
import { importNotes } from "../api/notesApi";
import * as workoutsApi from "../api/workoutsApi";
import type { WorkoutDetail } from "../api/types";
import { useToast } from "../components/Toast";
import {
  EmptyState,
  ErrorBanner,
  PageHeader,
  StatusBadge,
  formatNumber,
} from "../components/ui";

type Phase = "compose" | "parsing" | "review";

export function ImportNotesPage() {
  const { push } = useToast();
  const [rawText, setRawText] = useState("Bench 3x8 135 lb, Squat 5x5 185 lb");
  const [pending, setPending] = useState<WorkoutDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [phase, setPhase] = useState<Phase>("compose");
  const [message, setMessage] = useState<string | null>(null);

  async function onImport(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    setMessage(null);
    setPhase("parsing");
    try {
      const workout = await importNotes(rawText);
      setPending(workout);
      setPhase("review");
      push("Draft ready for review", "success");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Import failed.");
      setPending(null);
      setPhase("compose");
    } finally {
      setBusy(false);
    }
  }

  async function confirm() {
    if (!pending) return;
    setBusy(true);
    setError(null);
    try {
      const updated = await workoutsApi.confirmImport(pending.id);
      setPending(updated);
      setMessage("Import confirmed. It now counts toward stats.");
      push("Import confirmed", "success");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Confirm failed.");
    } finally {
      setBusy(false);
    }
  }

  async function reject() {
    if (!pending) return;
    setBusy(true);
    setError(null);
    try {
      const updated = await workoutsApi.rejectImport(pending.id);
      setPending(updated);
      setMessage("Import rejected. It will not count toward stats.");
      push("Import rejected", "success");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Reject failed.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="stack">
      <PageHeader
        title="Import notes"
        subtitle="Paste unstructured notes, review the draft, then confirm."
      />

      <div className="row" style={{ gap: "0.45rem" }}>
        <span className={`chip${phase === "compose" ? " active" : ""}`}>1. Compose</span>
        <span className={`chip${phase === "parsing" ? " active" : ""}`}>2. Parse</span>
        <span className={`chip${phase === "review" ? " active" : ""}`}>3. Review</span>
      </div>

      {error ? <ErrorBanner message={error} /> : null}
      {message ? (
        <div
          className="card"
          style={{
            borderColor: "color-mix(in srgb, var(--color-progress) 45%, var(--color-border))",
          }}
        >
          {message}
        </div>
      ) : null}

      <div className="split-pane">
        <form
          className={`card stack${phase === "review" ? " import-pane-hide-mobile" : ""}`}
          onSubmit={onImport}
        >
          <h2>Raw notes</h2>
          <div className="field">
            <label htmlFor="rawText">Workout text</label>
            <textarea
              id="rawText"
              value={rawText}
              onChange={(e) => {
                setRawText(e.target.value);
                if (phase !== "compose") setPhase("compose");
              }}
              required
              maxLength={10000}
              rows={14}
              style={{ minHeight: 280 }}
              placeholder={"Bench 3x8 135 lb\nSquat 5x5 185 lb"}
            />
          </div>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy && phase === "parsing" ? "Parsing…" : "Parse & preview"}
          </button>
        </form>

        <section
          className={`card stack${phase === "compose" && !pending ? " import-pane-hide-mobile" : ""}`}
          style={{ minHeight: 320 }}
        >
          <div className="row" style={{ justifyContent: "space-between" }}>
            <h2>Preview</h2>
            <div className="row">
              {pending ? <StatusBadge status={pending.importStatus} /> : null}
              {phase === "review" ? (
                <button
                  type="button"
                  className="btn btn-ghost import-mobile-only"
                  onClick={() => setPhase("compose")}
                >
                  Back to notes
                </button>
              ) : null}
            </div>
          </div>

          {phase === "parsing" ? (
            <EmptyState title="Parsing notes…" description="Building a draft workout for review." />
          ) : null}

          {!pending && phase !== "parsing" ? (
            <EmptyState
              title="No import to review"
              description="Submit notes on the left to create a PENDING_REVIEW workout."
            />
          ) : null}

          {pending ? (
            <>
              <p className="muted" style={{ margin: 0, fontSize: "var(--text-sm)" }}>
                Source {pending.source}. Imported workouts stay out of official stats until confirmed.
              </p>
              {pending.sets.length === 0 ? (
                <EmptyState title="Parser returned no sets" description="Try clearer set notation." />
              ) : (
                <div className="stack" style={{ gap: "0.55rem" }}>
                  {pending.sets.map((set) => (
                    <div key={set.id} className="set-card">
                      <div>
                        <div style={{ fontWeight: 650 }}>
                          {set.exerciseName} · Set {set.setNumber}
                        </div>
                        <div className="muted" style={{ fontSize: "var(--text-sm)" }}>
                          {set.exerciseCategory.replaceAll("_", " ")}
                        </div>
                      </div>
                      <div className="mono" style={{ fontWeight: 700 }}>
                        {formatNumber(set.weight, 1)} × {set.reps}
                      </div>
                    </div>
                  ))}
                </div>
              )}
              {pending.importStatus === "PENDING_REVIEW" ? (
                <div className="row">
                  <button
                    type="button"
                    className="btn btn-primary"
                    disabled={busy}
                    onClick={() => void confirm()}
                  >
                    Confirm import
                  </button>
                  <button
                    type="button"
                    className="btn btn-danger"
                    disabled={busy}
                    onClick={() => void reject()}
                  >
                    Reject import
                  </button>
                </div>
              ) : (
                <Link className="btn btn-secondary" to={`/workouts/${pending.id}`}>
                  View workout
                </Link>
              )}
            </>
          ) : null}
        </section>
      </div>
    </div>
  );
}
