import { apiFetch, toQuery } from "./http";
import type {
  NextSessionRecommendation,
  PersonalRecord,
  PrHistory,
  VolumeTrend,
  WeeklyStats,
} from "./types";

export function weeklyStats(date?: string) {
  return apiFetch<WeeklyStats>(`/api/stats/weekly${toQuery({ date })}`);
}

export function volumeTrend(weeks = 12) {
  return apiFetch<VolumeTrend>(`/api/stats/volume-trend${toQuery({ weeks })}`);
}

export function personalRecord(exerciseId: string) {
  return apiFetch<PersonalRecord>(`/api/exercises/${exerciseId}/pr`);
}

export function prHistory(exerciseId: string) {
  return apiFetch<PrHistory>(`/api/exercises/${exerciseId}/pr-history`);
}

export function nextSessionRecommendation(exerciseId: string) {
  return apiFetch<NextSessionRecommendation>(
    `/api/exercises/${exerciseId}/next-session-recommendation`,
  );
}
