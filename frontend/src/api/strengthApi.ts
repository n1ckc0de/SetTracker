import { apiFetch } from "./http";
import type { Estimate1RmResponse } from "./types";

export function estimate1Rm(body: {
  exerciseName?: string;
  weight: number;
  reps: number;
  rir?: number;
}) {
  return apiFetch<Estimate1RmResponse>("/api/strength/estimate-1rm", {
    method: "POST",
    body: JSON.stringify(body),
  });
}
