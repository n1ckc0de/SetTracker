import { apiFetch, toQuery } from "./http";
import type {
  Exercise,
  ExerciseCategory,
  ExerciseDetail,
  ExerciseMediaSuggestion,
  MediaSuggestionStatus,
  PageResponse,
} from "./types";

export function listExercises(params: {
  query?: string;
  category?: ExerciseCategory | "";
  page?: number;
  size?: number;
  sort?: string;
}) {
  return apiFetch<PageResponse<Exercise>>(
    `/api/exercises${toQuery({
      query: params.query,
      category: params.category || undefined,
      page: params.page ?? 0,
      size: params.size ?? 20,
      sort: params.sort ?? "name,asc",
    })}`,
  );
}

export function getExercise(exerciseId: string) {
  return apiFetch<Exercise>(`/api/exercises/${exerciseId}`);
}

export function getExerciseDetail(exerciseId: string) {
  return apiFetch<ExerciseDetail>(`/api/exercises/${exerciseId}/detail`);
}

export function createExercise(
  name: string,
  category: ExerciseCategory,
  media?: {
    thumbnailUrl?: string;
    demoVideoUrl?: string;
    mediaAttribution?: string;
  },
) {
  return apiFetch<Exercise>("/api/exercises", {
    method: "POST",
    body: JSON.stringify({
      name,
      category,
      thumbnailUrl: media?.thumbnailUrl || null,
      demoVideoUrl: media?.demoVideoUrl || null,
      mediaAttribution: media?.mediaAttribution || null,
    }),
  });
}

export function updateExercise(
  exerciseId: string,
  name: string,
  category: ExerciseCategory,
  media?: {
    thumbnailUrl?: string;
    demoVideoUrl?: string;
    mediaAttribution?: string;
  },
) {
  return apiFetch<Exercise>(`/api/exercises/${exerciseId}`, {
    method: "PUT",
    body: JSON.stringify({
      name,
      category,
      thumbnailUrl: media?.thumbnailUrl || null,
      demoVideoUrl: media?.demoVideoUrl || null,
      mediaAttribution: media?.mediaAttribution || null,
    }),
  });
}

export function deleteExercise(exerciseId: string) {
  return apiFetch<void>(`/api/exercises/${exerciseId}`, { method: "DELETE" });
}

export function generateMediaSuggestions(exerciseId: string) {
  return apiFetch<ExerciseMediaSuggestion[]>(`/api/exercises/${exerciseId}/media-suggestions`, {
    method: "POST",
  });
}

export function listMediaSuggestions(exerciseId: string, status: MediaSuggestionStatus = "PENDING") {
  return apiFetch<ExerciseMediaSuggestion[]>(
    `/api/exercises/${exerciseId}/media-suggestions${toQuery({ status })}`,
  );
}

export function confirmMediaSuggestion(exerciseId: string, suggestionId: string) {
  return apiFetch<ExerciseMediaSuggestion>(
    `/api/exercises/${exerciseId}/media-suggestions/${suggestionId}/confirm`,
    { method: "PATCH" },
  );
}

export function rejectMediaSuggestion(exerciseId: string, suggestionId: string) {
  return apiFetch<ExerciseMediaSuggestion>(
    `/api/exercises/${exerciseId}/media-suggestions/${suggestionId}/reject`,
    { method: "PATCH" },
  );
}
