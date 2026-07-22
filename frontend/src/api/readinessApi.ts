import { apiFetch, toQuery } from "./http";
import type { ReadinessCheckIn } from "./types";

export type CreateReadinessPayload = {
  workoutId?: string | null;
  sleepHours?: number | null;
  sleepQuality?: number | null;
  energyLevel?: number | null;
  soreness?: number | null;
  stress?: number | null;
  note?: string | null;
};

export function createReadiness(payload: CreateReadinessPayload) {
  return apiFetch<ReadinessCheckIn>("/api/readiness", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function latestReadiness() {
  return apiFetch<ReadinessCheckIn>("/api/readiness/latest");
}

export function listReadiness(from?: string, to?: string) {
  return apiFetch<ReadinessCheckIn[]>(`/api/readiness${toQuery({ from, to })}`);
}
