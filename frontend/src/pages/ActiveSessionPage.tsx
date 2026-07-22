import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { ApiError } from "../api/http";
import * as coachApi from "../api/coachApi";
import { listExercises } from "../api/exercisesApi";
import {
  clearPlannedExercises,
  listPrograms,
  loadPlannedExercises,
  startProgramDay,
  storePlannedExercises,
} from "../api/programsApi";
import * as statsApi from "../api/statsApi";
import * as workoutsApi from "../api/workoutsApi";
import type {
  CoachApplyPreview,
  CoachRecommendation,
  Exercise,
  ExerciseCategory,
  PlannedExercise,
  PrDetectionResult,
  ProgramSummary,
  SetType,
  WeightEntryMode,
  Workout,
  WorkoutDetail,
  WorkoutSet,
} from "../api/types";
import { Modal } from "../components/Modal";
import { RecommendationCard } from "../components/RecommendationCard";
import { useToast } from "../components/Toast";
import {
  EmptyState,
  ErrorBanner,
  LivePulseBar,
  LoadingBlock,
  PageHeader,
  StatusBadge,
  formatDateTime,
  formatDuration,
  formatNumber,
} from "../components/ui";
import { useSessionRestTimer } from "../context/SessionRestTimerContext";
import { getCurrentProgram } from "../lib/currentProgram";
import { useSessionSocket } from "../ws/SessionSocketContext";
import {
  DEFAULT_BAR_WEIGHT,
  SET_TYPE_OPTIONS,
  buildBatchSetPayload,
  countSetTypes,
  formatWeightDisplay,
  groupSetsByExercise,
  isBarbellCategory,
  resolvePrefill,
  toNum,
  type PrefillValues,
} from "./activeSessionHelpers";

function formatRest(seconds: number): string {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${m}:${String(s).padStart(2, "0")}`;
}

function showPrToasts(push: (msg: string, tone: "success" | "error") => void, prEvent?: PrDetectionResult | null) {
  if (!prEvent) return;
  if (prEvent.weightPr) {
    push("Weight PR!", "success");
  }
  if (prEvent.estimatedOneRepMaxPr) {
    push("Estimated 1RM PR!", "success");
  }
}

type SetRowProps = {
  set: WorkoutSet;
  isBarbell: boolean;
  disabled: boolean;
  onUpdated: (set: WorkoutSet) => void;
  onCompleted: (set: WorkoutSet) => void;
  onDeleted: (setId: string) => void;
};

function SetRow({ set, isBarbell, disabled, onUpdated, onCompleted, onDeleted }: SetRowProps) {
  const [enteredWeight, setEnteredWeight] = useState(String(set.enteredWeight));
  const [reps, setReps] = useState(String(set.reps));
  const [setType, setSetType] = useState<SetType>(set.setType);
  const [rir, setRir] = useState(set.rir != null ? String(set.rir) : "");
  const [weightEntryMode, setWeightEntryMode] = useState<WeightEntryMode>(set.weightEntryMode);
  const [barWeight, setBarWeight] = useState(
    set.barWeight != null ? String(set.barWeight) : String(DEFAULT_BAR_WEIGHT),
  );
  const [note, setNote] = useState(set.note ?? "");
  const [showNote, setShowNote] = useState(Boolean(set.note));
  const [saving, setSaving] = useState(false);
  const [completing, setCompleting] = useState(false);

  useEffect(() => {
    setEnteredWeight(String(set.enteredWeight));
    setReps(String(set.reps));
    setSetType(set.setType);
    setRir(set.rir != null ? String(set.rir) : "");
    setWeightEntryMode(set.weightEntryMode);
    setBarWeight(set.barWeight != null ? String(set.barWeight) : String(DEFAULT_BAR_WEIGHT));
    setNote(set.note ?? "");
    setShowNote(Boolean(set.note));
  }, [set]);

  async function persist(overrides?: { weightEntryMode?: WeightEntryMode }) {
    if (disabled || saving) return;
    const mode = overrides?.weightEntryMode ?? weightEntryMode;
    setSaving(true);
    try {
      const updated = await workoutsApi.updateSet(set.id, {
        enteredWeight: Number(enteredWeight),
        weightEntryMode: mode,
        barWeight: mode === "PER_SIDE" ? Number(barWeight) : null,
        reps: Number(reps),
        setNumber: set.setNumber,
        setType,
        rir: rir.trim() ? Number(rir) : null,
        note: note.trim() ? note.trim() : null,
        completed: set.completed,
      });
      onUpdated(updated);
    } finally {
      setSaving(false);
    }
  }

  async function onComplete() {
    if (disabled || completing || set.completed) return;
    setCompleting(true);
    try {
      await persist();
      const updated = await workoutsApi.completeSet(set.id);
      onCompleted(updated);
    } finally {
      setCompleting(false);
    }
  }

  return (
    <tr className={set.completed ? "set-row-done" : undefined}>
      <td className="mono">{set.setNumber}</td>
      <td>
        <select
          className="mono"
          value={setType}
          disabled={disabled || set.completed}
          onChange={(e) => setSetType(e.target.value as SetType)}
          onBlur={() => void persist()}
        >
          {SET_TYPE_OPTIONS.map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>
      </td>
      <td>
        <div className="stack" style={{ gap: "0.35rem" }}>
          {isBarbell ? (
            <div className="row" style={{ gap: "0.25rem" }}>
              <button
                type="button"
                className={`chip${weightEntryMode === "TOTAL" ? " active" : ""}`}
                disabled={disabled || set.completed}
                onClick={() => {
                  setWeightEntryMode("TOTAL");
                  void persist({ weightEntryMode: "TOTAL" });
                }}
              >
                Total
              </button>
              <button
                type="button"
                className={`chip${weightEntryMode === "PER_SIDE" ? " active" : ""}`}
                disabled={disabled || set.completed}
                onClick={() => {
                  setWeightEntryMode("PER_SIDE");
                  void persist({ weightEntryMode: "PER_SIDE" });
                }}
              >
                Per-side
              </button>
            </div>
          ) : null}
          <div className="row" style={{ gap: "0.35rem", flexWrap: "wrap" }}>
            <input
              className="mono"
              type="number"
              min={0}
              step="0.5"
              value={enteredWeight}
              disabled={disabled || set.completed}
              onChange={(e) => setEnteredWeight(e.target.value)}
              onBlur={() => void persist()}
              aria-label="Entered weight"
              style={{ width: "5.5rem" }}
            />
            {weightEntryMode === "PER_SIDE" ? (
              <label className="row muted" style={{ fontSize: "var(--text-xs)", gap: "0.25rem" }}>
                bar
                <input
                  className="mono"
                  type="number"
                  min={0}
                  step="0.5"
                  value={barWeight}
                  disabled={disabled || set.completed}
                  onChange={(e) => setBarWeight(e.target.value)}
                  onBlur={() => void persist()}
                  style={{ width: "4rem" }}
                />
              </label>
            ) : null}
          </div>
          {set.completed ? (
            <span className="muted mono" style={{ fontSize: "var(--text-xs)" }}>
              {formatWeightDisplay(set)}
            </span>
          ) : null}
        </div>
      </td>
      <td>
        <input
          className="mono"
          type="number"
          min={1}
          value={reps}
          disabled={disabled || set.completed}
          onChange={(e) => setReps(e.target.value)}
          onBlur={() => void persist()}
          style={{ width: "4rem" }}
        />
      </td>
      <td>
        <input
          className="mono"
          type="number"
          min={0}
          max={10}
          value={rir}
          disabled={disabled || set.completed}
          onChange={(e) => setRir(e.target.value)}
          onBlur={() => void persist()}
          placeholder="—"
          style={{ width: "3.5rem" }}
        />
      </td>
      <td>
        <div className="row" style={{ gap: "0.35rem", flexWrap: "wrap" }}>
          <button
            type="button"
            className={`btn${set.completed ? " btn-secondary" : " btn-primary"}`}
            disabled={disabled || completing || set.completed}
            onClick={() => void onComplete()}
            style={{ minHeight: 34, padding: "0.25rem 0.65rem" }}
          >
            {set.completed ? "Done" : completing ? "…" : "Complete"}
          </button>
          <button
            type="button"
            className="btn btn-ghost"
            disabled={disabled}
            onClick={() => setShowNote((v) => !v)}
            style={{ minHeight: 34, padding: "0.25rem 0.55rem" }}
          >
            Note
          </button>
          <button
            type="button"
            className="btn btn-ghost"
            disabled={disabled}
            onClick={() => void onDeleted(set.id)}
            style={{ minHeight: 34, padding: "0.25rem 0.55rem", color: "var(--color-danger)" }}
          >
            Remove
          </button>
        </div>
        {showNote ? (
          <textarea
            className="field"
            rows={2}
            value={note}
            disabled={disabled}
            onChange={(e) => setNote(e.target.value)}
            onBlur={() => void persist()}
            placeholder="Set note…"
            style={{ marginTop: "0.45rem", width: "100%", minWidth: "10rem" }}
          />
        ) : null}
      </td>
    </tr>
  );
}

type AddSetFieldsProps = {
  workoutId: string;
  exerciseId: string;
  category: ExerciseCategory;
  sessionSets: WorkoutSet[];
  planned: PlannedExercise | null;
  coachRec: CoachRecommendation | null;
  disabled: boolean;
  onAdded: () => Promise<void>;
};

function AddSetFields({
  workoutId,
  exerciseId,
  category,
  sessionSets,
  planned,
  coachRec,
  disabled,
  onAdded,
}: AddSetFieldsProps) {
  const [count, setCount] = useState("1");
  const [enteredWeight, setEnteredWeight] = useState("135");
  const [reps, setReps] = useState("8");
  const [setType, setSetType] = useState<SetType>("WORKING");
  const [rir, setRir] = useState("");
  const [note, setNote] = useState("");
  const [weightEntryMode, setWeightEntryMode] = useState<WeightEntryMode>("TOTAL");
  const [barWeight, setBarWeight] = useState(String(DEFAULT_BAR_WEIGHT));
  const [sourceLabel, setSourceLabel] = useState("");
  const [busy, setBusy] = useState(false);
  const [lastSession, setLastSession] = useState<{ weight: number | string; reps: number } | null>(
    null,
  );

  const isBarbell = isBarbellCategory(category);
  const exerciseSets = useMemo(
    () => sessionSets.filter((s) => s.exerciseId === exerciseId),
    [sessionSets, exerciseId],
  );
  const startingSetNumber = exerciseSets.length + 1;

  useEffect(() => {
    let cancelled = false;
    void statsApi
      .nextSessionRecommendation(exerciseId)
      .then((rec) => {
        if (!cancelled) setLastSession(rec.previousPerformance);
      })
      .catch(() => {
        if (!cancelled) setLastSession(null);
      });
    return () => {
      cancelled = true;
    };
  }, [exerciseId]);

  useEffect(() => {
    const prefill: PrefillValues = resolvePrefill({
      exerciseId,
      category,
      sessionSets,
      planned,
      coachRec,
      lastSession,
    });
    setEnteredWeight(prefill.enteredWeight);
    setReps(prefill.reps);
    setSetType(prefill.setType);
    setRir(prefill.rir);
    setWeightEntryMode(prefill.weightEntryMode);
    setBarWeight(prefill.barWeight);
    setSourceLabel(prefill.sourceLabel);
  }, [exerciseId, category, sessionSets, planned, coachRec, lastSession]);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (disabled || busy) return;
    setBusy(true);
    try {
      const payload = buildBatchSetPayload({
        exerciseId,
        count: Math.max(1, Number(count) || 1),
        startingSetNumber,
        enteredWeight: Number(enteredWeight),
        weightEntryMode,
        barWeight: weightEntryMode === "PER_SIDE" ? Number(barWeight) : undefined,
        reps: Number(reps),
        setType,
        rir: rir.trim() ? Number(rir) : undefined,
        note: note.trim() || undefined,
      });
      await workoutsApi.addBatchSets(workoutId, payload);
      setCount("1");
      setNote("");
      await onAdded();
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="stack" style={{ gap: "0.75rem" }} onSubmit={(e) => void onSubmit(e)}>
      <div className="row" style={{ justifyContent: "space-between", flexWrap: "wrap" }}>
        <strong>Add sets</strong>
        {sourceLabel ? (
          <span className="badge badge-coach" style={{ fontSize: "var(--text-xs)" }}>
            {sourceLabel}
          </span>
        ) : null}
      </div>
      <div className="grid-2">
        <div className="field">
          <label htmlFor={`count-${exerciseId}`}>Count</label>
          <input
            id={`count-${exerciseId}`}
            className="mono"
            type="number"
            min={1}
            max={20}
            value={count}
            disabled={disabled || busy}
            onChange={(e) => setCount(e.target.value)}
            required
          />
        </div>
        <div className="field">
          <label htmlFor={`type-${exerciseId}`}>Type</label>
          <select
            id={`type-${exerciseId}`}
            value={setType}
            disabled={disabled || busy}
            onChange={(e) => setSetType(e.target.value as SetType)}
          >
            {SET_TYPE_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
        </div>
        {isBarbell ? (
          <div className="field" style={{ gridColumn: "1 / -1" }}>
            <span className="muted" style={{ fontSize: "var(--text-xs)", display: "block", marginBottom: "0.35rem" }}>
              Weight entry
            </span>
            <div className="row" style={{ gap: "0.35rem" }}>
              <button
                type="button"
                className={`chip${weightEntryMode === "TOTAL" ? " active" : ""}`}
                disabled={disabled || busy}
                onClick={() => setWeightEntryMode("TOTAL")}
              >
                Total
              </button>
              <button
                type="button"
                className={`chip${weightEntryMode === "PER_SIDE" ? " active" : ""}`}
                disabled={disabled || busy}
                onClick={() => setWeightEntryMode("PER_SIDE")}
              >
                Per-side
              </button>
            </div>
          </div>
        ) : null}
        <div className="field">
          <label htmlFor={`weight-${exerciseId}`}>
            {weightEntryMode === "PER_SIDE" ? "Weight / side" : "Weight"}
          </label>
          <input
            id={`weight-${exerciseId}`}
            className="mono"
            type="number"
            min={0}
            step="0.5"
            value={enteredWeight}
            disabled={disabled || busy}
            onChange={(e) => setEnteredWeight(e.target.value)}
            required
          />
        </div>
        {weightEntryMode === "PER_SIDE" ? (
          <div className="field">
            <label htmlFor={`bar-${exerciseId}`}>Bar weight</label>
            <input
              id={`bar-${exerciseId}`}
              className="mono"
              type="number"
              min={0}
              step="0.5"
              value={barWeight}
              disabled={disabled || busy}
              onChange={(e) => setBarWeight(e.target.value)}
            />
          </div>
        ) : null}
        <div className="field">
          <label htmlFor={`reps-${exerciseId}`}>Reps</label>
          <input
            id={`reps-${exerciseId}`}
            className="mono"
            type="number"
            min={1}
            value={reps}
            disabled={disabled || busy}
            onChange={(e) => setReps(e.target.value)}
            required
          />
        </div>
        <div className="field">
          <label htmlFor={`rir-${exerciseId}`}>RIR</label>
          <input
            id={`rir-${exerciseId}`}
            className="mono"
            type="number"
            min={0}
            max={10}
            value={rir}
            disabled={disabled || busy}
            onChange={(e) => setRir(e.target.value)}
            placeholder="optional"
          />
        </div>
        <div className="field" style={{ gridColumn: "1 / -1" }}>
          <label htmlFor={`note-${exerciseId}`}>Set note (optional)</label>
          <input
            id={`note-${exerciseId}`}
            value={note}
            disabled={disabled || busy}
            onChange={(e) => setNote(e.target.value)}
            placeholder="Applies to all sets in batch"
          />
        </div>
      </div>
      <div className="row" style={{ justifyContent: "space-between", flexWrap: "wrap" }}>
        <span className="muted mono" style={{ fontSize: "var(--text-sm)" }}>
          Starting at set #{startingSetNumber}
        </span>
        <button type="submit" className="btn btn-primary" disabled={disabled || busy}>
          {busy ? "Adding…" : `Add ${Math.max(1, Number(count) || 1)} set${Number(count) > 1 ? "s" : ""}`}
        </button>
      </div>
    </form>
  );
}

export function ActiveSessionPage() {
  const { push } = useToast();
  const { connected, subscribeSession, lastUserEvent } = useSessionSocket();
  const { restEndsAt, restRemaining, startRest, addRestSeconds, skipRest, tick } = useSessionRestTimer();

  const [workout, setWorkout] = useState<WorkoutDetail | null>(null);
  const [exercises, setExercises] = useState<Exercise[]>([]);
  const [planned, setPlanned] = useState<PlannedExercise[]>([]);
  const [coachRecs, setCoachRecs] = useState<CoachRecommendation[]>([]);
  const [exerciseNotes, setExerciseNotes] = useState<Record<string, string>>({});
  const [noteDrafts, setNoteDrafts] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [addExerciseId, setAddExerciseId] = useState("");
  const [lastCompleted, setLastCompleted] = useState<Workout | null>(null);
  const [programs, setPrograms] = useState<ProgramSummary[]>([]);

  const [applyRec, setApplyRec] = useState<CoachRecommendation | null>(null);
  const [applyPreview, setApplyPreview] = useState<CoachApplyPreview | null>(null);
  const [applyOpen, setApplyOpen] = useState(false);
  const [applyLoading, setApplyLoading] = useState(false);

  const currentProgram = getCurrentProgram();

  const refreshWorkout = useCallback(async (workoutId: string) => {
    const detail = await workoutsApi.getWorkout(workoutId);
    setWorkout(detail);
    return detail;
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [activePage, exercisePage, completedPage, programList, proposedRecs] =
        await Promise.all([
          workoutsApi.listWorkouts({ status: "ACTIVE", size: 1 }),
          listExercises({ size: 200, sort: "name,asc" }),
          workoutsApi.listWorkouts({ status: "COMPLETED", size: 1 }),
          listPrograms().catch(() => [] as ProgramSummary[]),
          coachApi.listRecommendations("PROPOSED").catch(() => [] as CoachRecommendation[]),
        ]);
      setExercises(exercisePage.content);
      setLastCompleted(completedPage.content[0] ?? null);
      setPrograms(programList);
      setCoachRecs(proposedRecs);
      setAddExerciseId((current) => current || exercisePage.content[0]?.id || "");

      if (activePage.content[0]) {
        const detail = await refreshWorkout(activePage.content[0].id);
        setPlanned(loadPlannedExercises(detail.id));
        try {
          const notes = await workoutsApi.listExerciseNotes(detail.id);
          const map: Record<string, string> = {};
          for (const item of notes) {
            map[item.exerciseId] = item.note;
          }
          setExerciseNotes(map);
          setNoteDrafts(map);
        } catch {
          setExerciseNotes({});
          setNoteDrafts({});
        }
      } else {
        setWorkout(null);
        setPlanned([]);
        clearPlannedExercises();
        setExerciseNotes({});
        setNoteDrafts({});
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to load active session.");
    } finally {
      setLoading(false);
    }
  }, [refreshWorkout]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!lastUserEvent) return;
    void load();
  }, [lastUserEvent, load]);

  useEffect(() => {
    if (!workout || workout.status !== "ACTIVE") return;
    return subscribeSession(workout.id, () => {
      void refreshWorkout(workout.id).catch(() => undefined);
    });
  }, [workout?.id, workout?.status, subscribeSession, refreshWorkout]);

  useEffect(() => {
    if (restEndsAt == null) return;
    const id = window.setInterval(tick, 1000);
    return () => window.clearInterval(id);
  }, [restEndsAt, tick]);

  const live = useMemo(
    () => connected && workout?.status === "ACTIVE",
    [connected, workout?.status],
  );

  const exerciseGroups = useMemo(
    () => (workout ? groupSetsByExercise(workout.sets) : []),
    [workout],
  );

  const loggedExerciseIds = useMemo(
    () => new Set(exerciseGroups.map((g) => g.exerciseId)),
    [exerciseGroups],
  );

  const plannedOnly = useMemo(
    () => planned.filter((item) => !loggedExerciseIds.has(item.exerciseId)),
    [planned, loggedExerciseIds],
  );

  const volume = useMemo(() => {
    if (!workout) return 0;
    return workout.sets.reduce((sum, s) => sum + toNum(s.weight) * s.reps, 0);
  }, [workout]);

  const plannedProgress = useMemo(() => {
    if (planned.length === 0 || !workout) return null;
    const targetSets = planned.reduce((sum, p) => sum + p.targetSets, 0);
    return { logged: workout.sets.length, targetSets };
  }, [planned, workout]);

  const coachRecForExercise = useCallback(
    (exerciseId: string) =>
      coachRecs.find((rec) => rec.exerciseId === exerciseId && rec.status === "PROPOSED") ?? null,
    [coachRecs],
  );

  const plannedForExercise = useCallback(
    (exerciseId: string) => planned.find((item) => item.exerciseId === exerciseId) ?? null,
    [planned],
  );

  const categoryForExercise = useCallback(
    (exerciseId: string, fallback?: ExerciseCategory): ExerciseCategory => {
      const fromGroup = exerciseGroups.find((g) => g.exerciseId === exerciseId)?.exerciseCategory;
      if (fromGroup) return fromGroup;
      const fromList = exercises.find((e) => e.id === exerciseId)?.category;
      if (fromList) return fromList;
      return fallback ?? "FULL_BODY";
    },
    [exerciseGroups, exercises],
  );

  async function startBlank() {
    setBusy(true);
    setError(null);
    try {
      const started = await workoutsApi.startWorkout();
      await refreshWorkout(started.id);
      setPlanned([]);
      push("Blank session started", "success");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not start workout.");
    } finally {
      setBusy(false);
    }
  }

  async function continueCurrentProgram() {
    if (!currentProgram?.dayNumber) {
      setError("No current program day saved. Start a day from Programs first.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const result = await startProgramDay(currentProgram.id, currentProgram.dayNumber);
      storePlannedExercises(result.workout.id, result.plannedExercises);
      await refreshWorkout(result.workout.id);
      setPlanned(result.plannedExercises);
      push("Continued program day", "success");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not continue program.");
    } finally {
      setBusy(false);
    }
  }

  async function repeatLastWorkout() {
    if (!lastCompleted) return;
    setBusy(true);
    setError(null);
    try {
      const detail = await workoutsApi.getWorkout(lastCompleted.id);
      const created = await workoutsApi.startWorkout();
      const uniqueExercises = [...new Set(detail.sets.map((s) => s.exerciseId))];
      const plannedSeed: PlannedExercise[] = uniqueExercises.map((exId, index) => {
        const sample = detail.sets.find((s) => s.exerciseId === exId)!;
        const setsForEx = detail.sets.filter((s) => s.exerciseId === exId);
        return {
          id: `repeat-${exId}`,
          exerciseId: exId,
          exerciseName: sample.exerciseName,
          orderInDay: index + 1,
          targetSets: setsForEx.length,
          targetRepsMin: sample.reps,
          targetRepsMax: sample.reps,
          targetWeight: sample.enteredWeight ?? sample.weight,
        };
      });
      storePlannedExercises(created.id, plannedSeed);
      await refreshWorkout(created.id);
      setPlanned(plannedSeed);
      push("Repeated last workout structure — log fresh sets", "success");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not repeat last workout.");
    } finally {
      setBusy(false);
    }
  }

  async function onCompleteWorkout() {
    if (!workout) return;
    setBusy(true);
    setError(null);
    try {
      setWorkout(await workoutsApi.completeWorkout(workout.id));
      clearPlannedExercises();
      setPlanned([]);
      skipRest();
      push("Workout completed", "success");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not complete workout.");
    } finally {
      setBusy(false);
    }
  }

  async function onCancelWorkout() {
    if (!workout) return;
    setBusy(true);
    setError(null);
    try {
      await workoutsApi.cancelWorkout(workout.id);
      clearPlannedExercises();
      setPlanned([]);
      skipRest();
      setWorkout(null);
      push("Workout cancelled", "success");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not cancel workout.");
    } finally {
      setBusy(false);
    }
  }

  async function onSetUpdated(updated: WorkoutSet) {
    if (!workout) return;
    setWorkout({
      ...workout,
      sets: workout.sets.map((s) => (s.id === updated.id ? updated : s)),
    });
  }

  async function onSetCompleted(updated: WorkoutSet) {
    showPrToasts(push, updated.prEvent);
    if (updated.setType === "WORKING") {
      startRest("WORKING");
    }
    if (!workout) return;
    setWorkout({
      ...workout,
      sets: workout.sets.map((s) => (s.id === updated.id ? updated : s)),
    });
  }

  async function onSetDeleted(setId: string) {
    if (!workout) return;
    setBusy(true);
    try {
      await workoutsApi.deleteSet(setId);
      await refreshWorkout(workout.id);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not remove set.");
    } finally {
      setBusy(false);
    }
  }

  async function onSaveExerciseNote(exerciseId: string) {
    if (!workout) return;
    const note = noteDrafts[exerciseId] ?? "";
    setBusy(true);
    try {
      if (note.trim()) {
        await workoutsApi.upsertExerciseNote(workout.id, exerciseId, note.trim());
      }
      setExerciseNotes((prev) => ({ ...prev, [exerciseId]: note.trim() }));
      push("Exercise note saved", "success");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not save exercise note.");
    } finally {
      setBusy(false);
    }
  }

  async function openApplyPreview(rec: CoachRecommendation) {
    if (!workout) return;
    setApplyRec(rec);
    setApplyOpen(true);
    setApplyPreview(null);
    setApplyLoading(true);
    try {
      setApplyPreview(await coachApi.applyPreview(rec.id, workout.id));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not load apply preview.");
      setApplyOpen(false);
    } finally {
      setApplyLoading(false);
    }
  }

  async function confirmApply() {
    if (!workout || !applyRec) return;
    setApplyLoading(true);
    try {
      await coachApi.applyToSession(applyRec.id, workout.id);
      push("Coach recommendation applied to session", "success");
      setApplyOpen(false);
      setApplyRec(null);
      setApplyPreview(null);
      await refreshWorkout(workout.id);
      setCoachRecs(await coachApi.listRecommendations("PROPOSED"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not apply recommendation.");
    } finally {
      setApplyLoading(false);
    }
  }

  function renderExerciseCard(
    exerciseId: string,
    exerciseName: string,
    category: ExerciseCategory,
    sets: WorkoutSet[],
  ) {
    const counts = countSetTypes(sets);
    const coachRec = coachRecForExercise(exerciseId);
    const plannedItem = plannedForExercise(exerciseId);
    const isActive = workout?.status === "ACTIVE";

    return (
      <section key={exerciseId} className="card card-feature card-accent-train stack">
        <div className="row" style={{ justifyContent: "space-between", flexWrap: "wrap" }}>
          <div>
            <h2 style={{ fontSize: "var(--text-lg)", margin: 0 }}>{exerciseName}</h2>
            <div className="row" style={{ marginTop: "0.45rem", gap: "0.35rem" }}>
              <span className="badge">{category.replaceAll("_", " ")}</span>
              {counts.warmup > 0 ? (
                <span className="badge badge-warn">{counts.warmup} warm-up</span>
              ) : null}
              {counts.working > 0 ? (
                <span className="badge badge-active">{counts.working} working</span>
              ) : null}
            </div>
          </div>
          <Link to={`/exercises/${exerciseId}`} className="btn btn-ghost" style={{ minHeight: 36 }}>
            Details
          </Link>
        </div>

        <div className="field">
          <label htmlFor={`ex-note-${exerciseId}`}>Exercise note</label>
          <textarea
            id={`ex-note-${exerciseId}`}
            rows={2}
            value={noteDrafts[exerciseId] ?? exerciseNotes[exerciseId] ?? ""}
            disabled={!isActive || busy}
            onChange={(e) =>
              setNoteDrafts((prev) => ({ ...prev, [exerciseId]: e.target.value }))
            }
            placeholder="Cues, equipment, pain flags…"
          />
          {isActive ? (
            <button
              type="button"
              className="btn btn-secondary"
              disabled={busy}
              onClick={() => void onSaveExerciseNote(exerciseId)}
              style={{ marginTop: "0.45rem", alignSelf: "flex-start" }}
            >
              Save note
            </button>
          ) : null}
        </div>

        {coachRec ? (
          <RecommendationCard
            recommendation={coachRec}
            exerciseName={exerciseName}
            compact
            actions={
              isActive ? (
                <button
                  type="button"
                  className="btn btn-coach"
                  disabled={busy}
                  onClick={() => void openApplyPreview(coachRec)}
                >
                  Preview & apply
                </button>
              ) : null
            }
          />
        ) : null}

        {sets.length > 0 ? (
          <div style={{ overflowX: "auto" }}>
            <table className="table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>Type</th>
                  <th>Weight</th>
                  <th>Reps</th>
                  <th>RIR</th>
                  <th>Complete</th>
                </tr>
              </thead>
              <tbody>
                {sets.map((set) => (
                  <SetRow
                    key={set.id}
                    set={set}
                    isBarbell={isBarbellCategory(category)}
                    disabled={!isActive || busy}
                    onUpdated={(updated) => void onSetUpdated(updated)}
                    onCompleted={(updated) => void onSetCompleted(updated)}
                    onDeleted={(setId) => void onSetDeleted(setId)}
                  />
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <EmptyState
            title="No sets logged"
            description="Add your first sets below — batch add supported."
          />
        )}

        {isActive && workout ? (
          <AddSetFields
            workoutId={workout.id}
            exerciseId={exerciseId}
            category={category}
            sessionSets={workout.sets}
            planned={plannedItem}
            coachRec={coachRec}
            disabled={busy}
            onAdded={async () => {
              await refreshWorkout(workout.id);
            }}
          />
        ) : null}
      </section>
    );
  }

  if (loading) {
    return <LoadingBlock label="Loading session…" />;
  }

  if (!workout) {
    return (
      <div className="stack">
        <PageHeader
          title="Active session"
          subtitle="Live logging for your current workout."
        />
        {error ? <ErrorBanner message={error} onRetry={() => void load()} /> : null}
        <div className="card card-feature card-accent-train stack" style={{ padding: "1.75rem" }}>
          <EmptyState
            title="No active workout"
            description="Continue your program, repeat last session structure, or start blank."
            action={
              <div className="row">
                <button
                  type="button"
                  className="btn btn-primary"
                  disabled={busy}
                  onClick={() => void startBlank()}
                >
                  Start blank session
                </button>
                {currentProgram?.dayNumber ? (
                  <button
                    type="button"
                    className="btn btn-coach"
                    disabled={busy}
                    onClick={() => void continueCurrentProgram()}
                  >
                    Continue {currentProgram.name}
                  </button>
                ) : null}
                {lastCompleted ? (
                  <button
                    type="button"
                    className="btn btn-secondary"
                    disabled={busy}
                    onClick={() => void repeatLastWorkout()}
                  >
                    Repeat last workout
                  </button>
                ) : null}
                <Link className="btn btn-ghost" to="/programs">
                  {programs.length > 0 ? "Browse programs" : "Create a program"}
                </Link>
                <Link className="btn btn-ghost" to="/readiness">
                  Readiness tip
                </Link>
              </div>
            }
          />
          <p className="muted" style={{ margin: 0, fontSize: "var(--text-sm)" }}>
            Tip: a quick readiness check-in helps the coach weight suggestions safely.
          </p>
        </div>
      </div>
    );
  }

  const subtitleParts = [`Started ${formatDateTime(workout.startedAt)}`];
  if (currentProgram) {
    subtitleParts.push(
      `${currentProgram.name}${currentProgram.dayName ? ` · ${currentProgram.dayName}` : ""}`,
    );
  }

  return (
    <div className="stack">
      <PageHeader
        title="Active session"
        subtitle={subtitleParts.join(" · ")}
        actions={
          <>
            <StatusBadge status={workout.status} />
            {workout.status === "ACTIVE" ? (
              <>
                <button
                  type="button"
                  className="btn btn-primary"
                  disabled={busy}
                  onClick={() => void onCompleteWorkout()}
                >
                  Complete
                </button>
                <button
                  type="button"
                  className="btn btn-danger"
                  disabled={busy}
                  onClick={() => void onCancelWorkout()}
                >
                  Cancel
                </button>
              </>
            ) : null}
          </>
        }
      />

      {error ? <ErrorBanner message={error} onRetry={() => void load()} /> : null}

      <section className="card card-feature card-accent-train stack">
        <LivePulseBar live={!!live} />
        <div className="session-stats">
          <div>
            <div className="muted" style={{ fontSize: "var(--text-sm)" }}>Duration</div>
            <div className="metric mono" style={{ fontSize: "1.75rem" }}>
              {formatDuration(workout.startedAt, workout.status === "ACTIVE" ? null : workout.endedAt)}
            </div>
          </div>
          <div>
            <div className="muted" style={{ fontSize: "var(--text-sm)" }}>Volume</div>
            <div className="metric mono" style={{ fontSize: "1.75rem" }}>{formatNumber(volume, 0)}</div>
          </div>
          <div>
            <div className="muted" style={{ fontSize: "var(--text-sm)" }}>Progress</div>
            <div className="metric mono" style={{ fontSize: "1.75rem" }}>
              {plannedProgress
                ? `${plannedProgress.logged}/${plannedProgress.targetSets}`
                : `${workout.sets.length} sets`}
            </div>
          </div>
        </div>
        <div className="row" style={{ justifyContent: "space-between", flexWrap: "wrap" }}>
          <strong>{live ? "Live connection" : connected ? "Session idle" : "Socket offline"}</strong>
          <span className="muted mono">{workout.sets.length} sets logged</span>
        </div>
        {restEndsAt != null ? (
          <div className="row" style={{ justifyContent: "space-between", flexWrap: "wrap", marginTop: "0.65rem" }}>
            <span className="rest-timer">Rest {formatRest(restRemaining)}</span>
            <div className="row">
              <button
                type="button"
                className="btn btn-secondary"
                onClick={() => addRestSeconds(30)}
                style={{ minHeight: 36 }}
              >
                +30s
              </button>
              <button
                type="button"
                className="btn btn-ghost"
                onClick={skipRest}
                style={{ minHeight: 36 }}
              >
                Skip
              </button>
            </div>
          </div>
        ) : null}
      </section>

      {planned.length > 0 ? (
        <section className="card stack">
          <h2>Planned exercises</h2>
          <p className="muted" style={{ fontSize: "var(--text-sm)", margin: 0 }}>
            From your program day — targets prefill the add-set form when you log each exercise.
          </p>
          <div style={{ overflowX: "auto" }}>
            <table className="table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>Exercise</th>
                  <th>Target</th>
                  <th>Logged</th>
                </tr>
              </thead>
              <tbody>
                {planned.map((item) => {
                  const logged = workout.sets.filter((s) => s.exerciseId === item.exerciseId).length;
                  return (
                    <tr key={item.id}>
                      <td className="mono">{item.orderInDay}</td>
                      <td>{item.exerciseName}</td>
                      <td className="mono">
                        {item.targetSets}×
                        {item.targetRepsMin ?? "?"}
                        {item.targetRepsMax != null ? `–${item.targetRepsMax}` : ""}
                        {item.targetWeight != null ? ` @ ${formatNumber(item.targetWeight, 1)}` : ""}
                      </td>
                      <td className="mono">
                        {logged}/{item.targetSets}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </section>
      ) : null}

      {exerciseGroups.map((group) =>
        renderExerciseCard(group.exerciseId, group.exerciseName, group.exerciseCategory, group.sets),
      )}

      {plannedOnly.map((item) =>
        renderExerciseCard(
          item.exerciseId,
          item.exerciseName,
          categoryForExercise(item.exerciseId),
          [],
        ),
      )}

      {workout.status === "ACTIVE" ? (
        <section className="card stack">
          <h2>Log another exercise</h2>
          <div className="field">
            <label htmlFor="add-exercise">Exercise</label>
            <select
              id="add-exercise"
              value={addExerciseId}
              onChange={(e) => setAddExerciseId(e.target.value)}
            >
              {exercises.map((exercise) => (
                <option key={exercise.id} value={exercise.id}>
                  {exercise.name}
                </option>
              ))}
            </select>
          </div>
        </section>
      ) : null}

      {workout.status === "ACTIVE" &&
      addExerciseId &&
      !loggedExerciseIds.has(addExerciseId) &&
      !plannedOnly.some((p) => p.exerciseId === addExerciseId)
        ? renderExerciseCard(
            addExerciseId,
            exercises.find((e) => e.id === addExerciseId)?.name ?? "Exercise",
            categoryForExercise(addExerciseId),
            [],
          )
        : null}

      {workout.sets.length === 0 && planned.length === 0 ? (
        <EmptyState
          title="No sets yet"
          description="Pick an exercise below or start logging from a program day."
        />
      ) : null}

      <Modal
        open={applyOpen}
        title="Apply coach recommendation"
        onClose={() => {
          if (applyLoading) return;
          setApplyOpen(false);
          setApplyRec(null);
          setApplyPreview(null);
        }}
        footer={
          <>
            <button
              type="button"
              className="btn btn-ghost"
              disabled={applyLoading}
              onClick={() => {
                setApplyOpen(false);
                setApplyRec(null);
                setApplyPreview(null);
              }}
            >
              Cancel
            </button>
            <button
              type="button"
              className="btn btn-coach"
              disabled={applyLoading || !applyPreview}
              onClick={() => void confirmApply()}
            >
              {applyLoading ? "Applying…" : "Apply to session"}
            </button>
          </>
        }
      >
        {applyRec ? (
          <RecommendationCard
            recommendation={applyRec}
            exerciseName={
              applyRec.exerciseId
                ? exercises.find((e) => e.id === applyRec.exerciseId)?.name
                : undefined
            }
            compact
          />
        ) : null}
        {applyLoading && !applyPreview ? (
          <LoadingBlock label="Loading preview…" />
        ) : null}
        {applyPreview ? (
          <div style={{ overflowX: "auto" }}>
            <table className="table">
              <thead>
                <tr>
                  <th>Set</th>
                  <th>Current</th>
                  <th>Proposed</th>
                  <th>Change?</th>
                </tr>
              </thead>
              <tbody>
                {applyPreview.sets.map((row) => (
                  <tr key={row.setId}>
                    <td className="mono">{row.setNumber}</td>
                    <td className="mono">
                      {formatNumber(row.currentWeight, 1)}
                      {row.currentReps != null ? ` × ${row.currentReps}` : ""}
                      {row.currentRir != null ? ` @ ${row.currentRir} RIR` : ""}
                    </td>
                    <td className="mono">
                      {row.proposedWeight != null ? formatNumber(row.proposedWeight, 1) : "—"}
                      {row.proposedReps != null ? ` × ${row.proposedReps}` : ""}
                      {row.proposedRir != null ? ` @ ${row.proposedRir} RIR` : ""}
                    </td>
                    <td>{row.willChange ? "Yes" : "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}
      </Modal>
    </div>
  );
}
