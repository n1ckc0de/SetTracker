import { describe, expect, it } from "vitest";
import {
  buildBatchSetPayload,
  groupSetsByExercise,
  isBarbellCategory,
  resolvePrefill,
} from "./activeSessionHelpers";
import type { CoachRecommendation, PlannedExercise, WorkoutSet } from "../api/types";

function sampleSet(overrides: Partial<WorkoutSet> = {}): WorkoutSet {
  return {
    id: overrides.id ?? "s1",
    exerciseId: overrides.exerciseId ?? "ex1",
    exerciseName: overrides.exerciseName ?? "Bench Press",
    exerciseCategory: overrides.exerciseCategory ?? "CHEST",
    weight: overrides.weight ?? 135,
    reps: overrides.reps ?? 8,
    setNumber: overrides.setNumber ?? 1,
    loggedAt: overrides.loggedAt ?? "2026-07-20T12:00:00Z",
    setType: overrides.setType ?? "WORKING",
    rir: overrides.rir ?? null,
    weightEntryMode: overrides.weightEntryMode ?? "TOTAL",
    barWeight: overrides.barWeight ?? null,
    enteredWeight: overrides.enteredWeight ?? 135,
    note: overrides.note ?? null,
    completed: overrides.completed ?? false,
    createdAt: overrides.createdAt ?? "2026-07-20T12:00:00Z",
    updatedAt: overrides.updatedAt ?? "2026-07-20T12:00:00Z",
  };
}

describe("activeSessionHelpers", () => {
  it("groups sets by first appearance order", () => {
    const groups = groupSetsByExercise([
      sampleSet({ id: "a", exerciseId: "ex1", exerciseName: "Bench", setNumber: 1 }),
      sampleSet({ id: "b", exerciseId: "ex2", exerciseName: "Row", setNumber: 1, exerciseCategory: "BACK" }),
      sampleSet({ id: "c", exerciseId: "ex1", exerciseName: "Bench", setNumber: 2 }),
    ]);
    expect(groups.map((g) => g.exerciseId)).toEqual(["ex1", "ex2"]);
    expect(groups[0].sets.map((s) => s.setNumber)).toEqual([1, 2]);
  });

  it("builds batch payload with enteredWeight and per-side bar", () => {
    const payload = buildBatchSetPayload({
      exerciseId: "ex1",
      count: 3,
      startingSetNumber: 2,
      enteredWeight: 50,
      weightEntryMode: "PER_SIDE",
      barWeight: 45,
      reps: 8,
      setType: "WORKING",
      rir: 2,
      note: "smooth",
    });
    expect(payload).toEqual({
      exerciseId: "ex1",
      count: 3,
      startingSetNumber: 2,
      enteredWeight: 50,
      weightEntryMode: "PER_SIDE",
      barWeight: 45,
      reps: 8,
      setType: "WORKING",
      rir: 2,
      note: "smooth",
    });
  });

  it("prefers previous set in session over coach and program", () => {
    const coachRec = {
      proposedChange: JSON.stringify({ proposedWeight: 200, proposedRepRangeMin: 5 }),
    } as CoachRecommendation;
    const planned = {
      targetWeight: 185,
      targetRepsMin: 6,
    } as PlannedExercise;

    const prefill = resolvePrefill({
      exerciseId: "ex1",
      category: "CHEST",
      sessionSets: [sampleSet({ enteredWeight: 155, reps: 7 })],
      planned,
      coachRec,
      lastSession: { weight: 150, reps: 8 },
    });

    expect(prefill.source).toBe("previous_set");
    expect(prefill.enteredWeight).toBe("155");
    expect(prefill.reps).toBe("7");
  });

  it("identifies barbell categories for per-side toggle", () => {
    expect(isBarbellCategory("CHEST")).toBe(true);
    expect(isBarbellCategory("ARMS")).toBe(false);
  });
});
