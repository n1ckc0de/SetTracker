import { apiFetch, toQuery } from "./http";
import type { CoachApplyPreview, CoachRecommendation, RecommendationStatus } from "./types";
import type { WorkoutSet } from "./types";

export function listRecommendations(status?: RecommendationStatus) {
  return apiFetch<CoachRecommendation[]>(
    `/api/coach/recommendations${toQuery({ status })}`,
  );
}

export function generateRecommendation(exerciseId?: string, workoutId?: string) {
  return apiFetch<CoachRecommendation>("/api/coach/recommendations/generate", {
    method: "POST",
    body: JSON.stringify({
      exerciseId: exerciseId || null,
      workoutId: workoutId || null,
    }),
  });
}

export function acceptRecommendation(id: string) {
  return apiFetch<CoachRecommendation>(`/api/coach/recommendations/${id}/accept`, {
    method: "PATCH",
  });
}

export function dismissRecommendation(id: string) {
  return apiFetch<CoachRecommendation>(`/api/coach/recommendations/${id}/dismiss`, {
    method: "PATCH",
  });
}

export function modifyRecommendation(id: string, proposedChange: string, reasoning?: string) {
  return apiFetch<CoachRecommendation>(`/api/coach/recommendations/${id}/modify`, {
    method: "PATCH",
    body: JSON.stringify({ proposedChange, reasoning: reasoning || null }),
  });
}

export function coachChat(message: string, exerciseId?: string, workoutId?: string) {
  return apiFetch<CoachRecommendation>("/api/coach/chat", {
    method: "POST",
    body: JSON.stringify({
      message,
      exerciseId: exerciseId || null,
      workoutId: workoutId || null,
    }),
  });
}

export function applyPreview(recommendationId: string, workoutId: string) {
  return apiFetch<CoachApplyPreview>(
    `/api/coach/recommendations/${recommendationId}/apply-preview${toQuery({ workoutId })}`,
  );
}

export function applyToSession(recommendationId: string, workoutId: string) {
  return apiFetch<WorkoutSet[]>(`/api/coach/recommendations/${recommendationId}/apply`, {
    method: "POST",
    body: JSON.stringify({ workoutId }),
  });
}
