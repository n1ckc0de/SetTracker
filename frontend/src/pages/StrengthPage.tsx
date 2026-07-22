import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { estimate1Rm } from "../api/strengthApi";
import { listExercises } from "../api/exercisesApi";
import type { Estimate1RmResponse, Exercise } from "../api/types";
import { ApiError } from "../api/http";
import { ErrorBanner, PageHeader, formatNumber } from "../components/ui";
import { useEffect } from "react";

export function StrengthPage() {
  const [exercises, setExercises] = useState<Exercise[]>([]);
  const [exerciseId, setExerciseId] = useState("");
  const [weight, setWeight] = useState("225");
  const [reps, setReps] = useState("5");
  const [rir, setRir] = useState("");
  const [result, setResult] = useState<Estimate1RmResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    void listExercises({ size: 100, sort: "name,asc" })
      .then((page) => {
        setExercises(page.content);
        setExerciseId((c) => c || page.content[0]?.id || "");
      })
      .catch(() => undefined);
  }, []);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const exercise = exercises.find((x) => x.id === exerciseId);
      setResult(
        await estimate1Rm({
          exerciseName: exercise?.name,
          weight: Number(weight),
          reps: Number(reps),
          rir: rir ? Number(rir) : undefined,
        }),
      );
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not estimate 1RM.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="stack">
      <PageHeader
        title="Strength"
        subtitle="Deterministic 1RM estimate — Epley + Brzycki average"
      />

      {error ? <ErrorBanner message={error} /> : null}

      <form className="card stack" onSubmit={(e) => void onSubmit(e)}>
        <label className="field">
          <span>Exercise (optional)</span>
          <select value={exerciseId} onChange={(e) => setExerciseId(e.target.value)}>
            <option value="">Freeform</option>
            {exercises.map((ex) => (
              <option key={ex.id} value={ex.id}>
                {ex.name}
              </option>
            ))}
          </select>
        </label>
        <div className="grid-2">
          <label className="field">
            <span>Weight (lb)</span>
            <input className="mono" value={weight} onChange={(e) => setWeight(e.target.value)} />
          </label>
          <label className="field">
            <span>Reps</span>
            <input className="mono" value={reps} onChange={(e) => setReps(e.target.value)} />
          </label>
        </div>
        <label className="field">
          <span>RIR (optional context)</span>
          <input className="mono" value={rir} onChange={(e) => setRir(e.target.value)} placeholder="0–5" />
        </label>
        <button type="submit" className="btn btn-primary" disabled={busy}>
          {busy ? "Calculating…" : "Estimate 1RM"}
        </button>
      </form>

      {result ? (
        <section className="card stack">
          <h2 className="mono">{formatNumber(result.workingEstimate, 1)} lb working estimate</h2>
          <p className="muted">{result.confidenceNote}</p>
          <div className="grid-2">
            <div>
              <div className="muted">Range</div>
              <div className="mono">
                {formatNumber(result.rangeMin, 1)} – {formatNumber(result.rangeMax, 1)}
              </div>
            </div>
            <div>
              <div className="muted">Formulas</div>
              <div className="mono">
                Epley {formatNumber(result.epley, 1)} · Brzycki {formatNumber(result.brzycki, 1)}
              </div>
            </div>
          </div>
          {result.rirContext != null ? (
            <p className="muted">RIR context on input set: {result.rirContext}</p>
          ) : null}
          <Link to="/coach" className="btn btn-coach">
            Ask Coach the same question
          </Link>
        </section>
      ) : null}
    </div>
  );
}
