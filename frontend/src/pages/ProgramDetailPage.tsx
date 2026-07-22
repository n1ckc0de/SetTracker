import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ApiError } from "../api/http";
import { listExercises } from "../api/exercisesApi";
import * as programsApi from "../api/programsApi";
import type { Exercise, ProgramDetail } from "../api/types";
import { useToast } from "../components/Toast";
import {
  EmptyState,
  ErrorBanner,
  LoadingBlock,
  PageHeader,
  formatNumber,
} from "../components/ui";

export function ProgramDetailPage() {
  const { programId = "" } = useParams();
  const navigate = useNavigate();
  const { push } = useToast();
  const [program, setProgram] = useState<ProgramDetail | null>(null);
  const [exercises, setExercises] = useState<Exercise[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const [dayNumber, setDayNumber] = useState("1");
  const [dayName, setDayName] = useState("Push Day");
  const [exerciseId, setExerciseId] = useState("");
  const [targetSets, setTargetSets] = useState("3");
  const [repsMin, setRepsMin] = useState("8");
  const [repsMax, setRepsMax] = useState("10");
  const [targetWeight, setTargetWeight] = useState("");
  const [draftExercises, setDraftExercises] = useState<
    Array<{
      exerciseId: string;
      exerciseName: string;
      orderInDay: number;
      targetSets: number;
      targetRepsMin: number | null;
      targetRepsMax: number | null;
      targetWeight: number | null;
    }>
  >([]);

  const load = useCallback(async () => {
    if (!programId) return;
    setLoading(true);
    setError(null);
    try {
      const [detail, exercisePage] = await Promise.all([
        programsApi.getProgram(programId),
        listExercises({ size: 100, sort: "name,asc" }),
      ]);
      setProgram(detail);
      setExercises(exercisePage.content);
      setExerciseId((current) => current || exercisePage.content[0]?.id || "");
      setDayNumber(String((detail.days.at(-1)?.dayNumber ?? 0) + 1));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to load program.");
      setProgram(null);
    } finally {
      setLoading(false);
    }
  }, [programId]);

  useEffect(() => {
    void load();
  }, [load]);

  function addDraftExercise() {
    const selected = exercises.find((e) => e.id === exerciseId);
    if (!selected) return;
    setDraftExercises((prev) => [
      ...prev,
      {
        exerciseId: selected.id,
        exerciseName: selected.name,
        orderInDay: prev.length + 1,
        targetSets: Number(targetSets),
        targetRepsMin: repsMin ? Number(repsMin) : null,
        targetRepsMax: repsMax ? Number(repsMax) : null,
        targetWeight: targetWeight ? Number(targetWeight) : null,
      },
    ]);
  }

  async function onAddDay(event: FormEvent) {
    event.preventDefault();
    if (!programId || draftExercises.length === 0) {
      setError("Add at least one planned exercise before saving the day.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await programsApi.addProgramDay(programId, {
        dayNumber: Number(dayNumber),
        name: dayName.trim(),
        exercises: draftExercises.map((item) => ({
          exerciseId: item.exerciseId,
          orderInDay: item.orderInDay,
          targetSets: item.targetSets,
          targetRepsMin: item.targetRepsMin,
          targetRepsMax: item.targetRepsMax,
          targetWeight: item.targetWeight,
        })),
      });
      setDraftExercises([]);
      push("Day added", "success");
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not add day.");
    } finally {
      setBusy(false);
    }
  }

  async function onStartDay(dayNumberValue: number) {
    if (!programId) return;
    setBusy(true);
    setError(null);
    try {
      const result = await programsApi.startProgramDay(programId, dayNumberValue);
      programsApi.storePlannedExercises(result.workout.id, result.plannedExercises);
      const day = program?.days.find((d) => d.dayNumber === dayNumberValue);
      const { setCurrentProgram } = await import("../lib/currentProgram");
      setCurrentProgram({
        id: programId,
        name: program?.name ?? "Program",
        dayNumber: dayNumberValue,
        dayName: day?.name,
      });
      push("Day started — log sets in session", "success");
      navigate("/session");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not start day.");
    } finally {
      setBusy(false);
    }
  }

  async function onDeleteDay(dayNumberValue: number) {
    if (!programId) return;
    setBusy(true);
    try {
      await programsApi.deleteProgramDay(programId, dayNumberValue);
      push("Day deleted", "success");
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete day.");
    } finally {
      setBusy(false);
    }
  }

  if (loading) {
    return <LoadingBlock label="Loading program…" />;
  }

  if (!program) {
    return (
      <div className="stack">
        <ErrorBanner
          message={error ?? "Program not found"}
          onRetry={() => void load()}
          backTo="/programs"
        />
      </div>
    );
  }

  return (
    <div className="stack">
      <Link to="/programs" className="muted" style={{ textDecoration: "none", width: "fit-content" }}>
        ← Programs
      </Link>

      <PageHeader
        title={program.name}
        subtitle={program.description || "No description"}
      />

      {error ? <ErrorBanner message={error} onRetry={() => void load()} /> : null}

      {program.days.length === 0 ? (
        <EmptyState title="No days yet" description="Add a day with planned exercises below." />
      ) : (
        <div className="card-grid">
          {program.days.map((day) => (
            <section key={day.id} className="card card-feature card-accent-train stack">
              <div className="row" style={{ justifyContent: "space-between" }}>
                <h2 style={{ fontSize: "var(--text-lg)" }}>
                  Day {day.dayNumber}: {day.name}
                </h2>
                <span className="badge badge-active">{day.exercises.length} exercises</span>
              </div>
              <div className="stack" style={{ gap: "0.45rem" }}>
                {day.exercises.map((item) => (
                  <div key={item.id} className="set-card" style={{ padding: "0.65rem 0.75rem" }}>
                    <div>
                      <div style={{ fontWeight: 650 }}>
                        {item.orderInDay}. {item.exerciseName}
                      </div>
                      <div className="mono muted" style={{ fontSize: "var(--text-sm)" }}>
                        {item.targetSets} sets
                        {item.targetRepsMin != null ? ` · ${item.targetRepsMin}` : ""}
                        {item.targetRepsMax != null ? `–${item.targetRepsMax}` : ""} reps
                        {item.targetWeight != null ? ` · ${formatNumber(item.targetWeight, 1)}` : ""}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
              <div className="row">
                <button
                  type="button"
                  className="btn btn-primary"
                  disabled={busy}
                  onClick={() => void onStartDay(day.dayNumber)}
                >
                  Start this day
                </button>
                <button
                  type="button"
                  className="btn btn-danger"
                  disabled={busy}
                  onClick={() => void onDeleteDay(day.dayNumber)}
                >
                  Delete day
                </button>
              </div>
            </section>
          ))}
        </div>
      )}

      <form className="card stack" onSubmit={onAddDay}>
        <h2>Add day</h2>
        <div className="grid-2">
          <div className="field">
            <label htmlFor="day-number">Day number</label>
            <input
              id="day-number"
              className="mono"
              type="number"
              min={1}
              value={dayNumber}
              onChange={(e) => setDayNumber(e.target.value)}
              required
            />
          </div>
          <div className="field">
            <label htmlFor="day-name">Name</label>
            <input
              id="day-name"
              value={dayName}
              onChange={(e) => setDayName(e.target.value)}
              required
            />
          </div>
        </div>

        <div className="stack" style={{ borderTop: "1px solid var(--color-border)", paddingTop: "1rem" }}>
          <h3 style={{ margin: 0, fontSize: "var(--text-md)" }}>Planned exercises</h3>
          {draftExercises.length === 0 ? (
            <p className="muted" style={{ margin: 0 }}>None yet — add exercises below.</p>
          ) : (
            <div className="stack" style={{ gap: "0.45rem" }}>
              {draftExercises.map((item) => (
                <div key={`${item.exerciseId}-${item.orderInDay}`} className="set-card" style={{ padding: "0.65rem 0.75rem" }}>
                  <span>
                    {item.orderInDay}. {item.exerciseName} · {item.targetSets} sets
                    {item.targetRepsMin != null ? ` · ${item.targetRepsMin}` : ""}
                    {item.targetRepsMax != null ? `–${item.targetRepsMax}` : ""} reps
                    {item.targetWeight != null ? ` · ${item.targetWeight}` : ""}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="grid-2">
          <div className="field">
            <label htmlFor="ex">Exercise</label>
            <select id="ex" value={exerciseId} onChange={(e) => setExerciseId(e.target.value)}>
              {exercises.map((ex) => (
                <option key={ex.id} value={ex.id}>
                  {ex.name}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="sets">Target sets</label>
            <input
              id="sets"
              className="mono"
              type="number"
              min={1}
              value={targetSets}
              onChange={(e) => setTargetSets(e.target.value)}
            />
          </div>
          <div className="field">
            <label htmlFor="rmin">Reps min</label>
            <input
              id="rmin"
              className="mono"
              type="number"
              min={1}
              value={repsMin}
              onChange={(e) => setRepsMin(e.target.value)}
            />
          </div>
          <div className="field">
            <label htmlFor="rmax">Reps max</label>
            <input
              id="rmax"
              className="mono"
              type="number"
              min={1}
              value={repsMax}
              onChange={(e) => setRepsMax(e.target.value)}
            />
          </div>
          <div className="field">
            <label htmlFor="tw">Suggested weight</label>
            <input
              id="tw"
              className="mono"
              type="number"
              min={0}
              step="0.5"
              value={targetWeight}
              onChange={(e) => setTargetWeight(e.target.value)}
            />
          </div>
        </div>
        <div className="row">
          <button type="button" className="btn btn-secondary" onClick={addDraftExercise}>
            Add exercise to day
          </button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            Save day
          </button>
        </div>
      </form>
    </div>
  );
}
