import { apiFetch } from "./http";
import type { ProgramDetail, ProgramSummary, StartProgramDayResponse, Workout } from "./types";

export function listPrograms() {
  return apiFetch<ProgramSummary[]>("/api/programs");
}

export function getProgram(programId: string) {
  return apiFetch<ProgramDetail>(`/api/programs/${programId}`);
}

export function createProgram(name: string, description?: string) {
  return apiFetch<ProgramSummary>("/api/programs", {
    method: "POST",
    body: JSON.stringify({ name, description: description || null }),
  });
}

export function updateProgram(programId: string, name: string, description?: string) {
  return apiFetch<ProgramSummary>(`/api/programs/${programId}`, {
    method: "PUT",
    body: JSON.stringify({ name, description: description || null }),
  });
}

export function deleteProgram(programId: string) {
  return apiFetch<void>(`/api/programs/${programId}`, { method: "DELETE" });
}

export function addProgramDay(
  programId: string,
  body: {
    dayNumber: number;
    name: string;
    exercises: Array<{
      exerciseId: string;
      orderInDay: number;
      targetSets: number;
      targetRepsMin?: number | null;
      targetRepsMax?: number | null;
      targetWeight?: number | null;
    }>;
  },
) {
  return apiFetch<ProgramDetail["days"][number]>(`/api/programs/${programId}/days`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export function updateProgramDay(
  programId: string,
  dayNumber: number,
  body: {
    name: string;
    exercises: Array<{
      exerciseId: string;
      orderInDay: number;
      targetSets: number;
      targetRepsMin?: number | null;
      targetRepsMax?: number | null;
      targetWeight?: number | null;
    }>;
  },
) {
  return apiFetch<ProgramDetail["days"][number]>(
    `/api/programs/${programId}/days/${dayNumber}`,
    {
      method: "PUT",
      body: JSON.stringify(body),
    },
  );
}

export function deleteProgramDay(programId: string, dayNumber: number) {
  return apiFetch<void>(`/api/programs/${programId}/days/${dayNumber}`, { method: "DELETE" });
}

export function startProgramDay(programId: string, dayNumber: number) {
  return apiFetch<StartProgramDayResponse>(
    `/api/programs/${programId}/days/${dayNumber}/start`,
    { method: "POST" },
  );
}

const PLANNED_KEY = "gymsync.plannedExercises";

export function storePlannedExercises(workoutId: string, planned: StartProgramDayResponse["plannedExercises"]) {
  sessionStorage.setItem(PLANNED_KEY, JSON.stringify({ workoutId, planned }));
}

export function loadPlannedExercises(workoutId: string): StartProgramDayResponse["plannedExercises"] {
  try {
    const raw = sessionStorage.getItem(PLANNED_KEY);
    if (!raw) {
      return [];
    }
    const parsed = JSON.parse(raw) as { workoutId: string; planned: StartProgramDayResponse["plannedExercises"] };
    if (parsed.workoutId !== workoutId) {
      return [];
    }
    return parsed.planned ?? [];
  } catch {
    return [];
  }
}

export function clearPlannedExercises() {
  sessionStorage.removeItem(PLANNED_KEY);
}

// re-export Workout type usage helper
export type { Workout };
