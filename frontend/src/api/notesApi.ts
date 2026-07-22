import { apiFetch } from "./http";
import type { WorkoutDetail } from "./types";

export function importNotes(rawText: string) {
  return apiFetch<WorkoutDetail>("/api/notes/import", {
    method: "POST",
    body: JSON.stringify({ rawText }),
  });
}
