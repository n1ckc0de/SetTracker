import { apiFetch, toQuery } from "./http";
import type {
  PageResponse,
  SetType,
  WeightEntryMode,
  Workout,
  WorkoutDetail,
  WorkoutExerciseNote,
  WorkoutSet,
  WorkoutSource,
  WorkoutStatus,
} from "./types";

export type SetPayload = {
  exerciseId: string;
  weight?: number;
  enteredWeight?: number;
  weightEntryMode?: WeightEntryMode;
  barWeight?: number;
  reps: number;
  setNumber: number;
  setType?: SetType;
  rir?: number;
  note?: string;
  completed?: boolean;
};

export function startWorkout() {
  return apiFetch<Workout>("/api/workouts", {
    method: "POST",
    body: JSON.stringify({}),
  });
}

export function listWorkouts(params: {
  status?: WorkoutStatus | "";
  source?: WorkoutSource | "";
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}) {
  return apiFetch<PageResponse<Workout>>(
    `/api/workouts${toQuery({
      status: params.status || undefined,
      source: params.source || undefined,
      from: params.from,
      to: params.to,
      page: params.page ?? 0,
      size: params.size ?? 20,
      sort: "startedAt,desc",
    })}`,
  );
}

export function getWorkout(workoutId: string) {
  return apiFetch<WorkoutDetail>(`/api/workouts/${workoutId}`);
}

export function completeWorkout(workoutId: string) {
  return apiFetch<WorkoutDetail>(`/api/workouts/${workoutId}/complete`, {
    method: "PATCH",
  });
}

export function cancelWorkout(workoutId: string) {
  return apiFetch<void>(`/api/workouts/${workoutId}`, { method: "DELETE" });
}

export function confirmImport(workoutId: string) {
  return apiFetch<WorkoutDetail>(`/api/workouts/${workoutId}/confirm-import`, {
    method: "PATCH",
  });
}

export function rejectImport(workoutId: string) {
  return apiFetch<WorkoutDetail>(`/api/workouts/${workoutId}/reject-import`, {
    method: "PATCH",
  });
}

export function listSets(workoutId: string) {
  return apiFetch<WorkoutSet[]>(`/api/workouts/${workoutId}/sets`);
}

export function listExerciseNotes(workoutId: string) {
  return apiFetch<WorkoutExerciseNote[]>(`/api/workouts/${workoutId}/exercise-notes`);
}

export function upsertExerciseNote(workoutId: string, exerciseId: string, note: string) {
  return apiFetch<WorkoutExerciseNote>(`/api/workouts/${workoutId}/exercises/${exerciseId}/note`, {
    method: "PUT",
    body: JSON.stringify({ note }),
  });
}

export function addSet(workoutId: string, body: SetPayload) {
  return apiFetch<WorkoutSet>(`/api/workouts/${workoutId}/sets`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export function addBatchSets(
  workoutId: string,
  body: Omit<SetPayload, "setNumber"> & { count: number; startingSetNumber?: number },
) {
  return apiFetch<WorkoutSet[]>(`/api/workouts/${workoutId}/sets/batch`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export function updateSet(
  setId: string,
  body: {
    enteredWeight: number;
    weightEntryMode: WeightEntryMode;
    barWeight?: number | null;
    reps: number;
    setNumber: number;
    setType?: SetType;
    rir?: number | null;
    note?: string | null;
    completed?: boolean;
  },
) {
  return apiFetch<WorkoutSet>(`/api/sets/${setId}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

export function completeSet(setId: string) {
  return apiFetch<WorkoutSet>(`/api/sets/${setId}/complete`, { method: "PATCH" });
}

export function deleteSet(setId: string) {
  return apiFetch<void>(`/api/sets/${setId}`, { method: "DELETE" });
}
