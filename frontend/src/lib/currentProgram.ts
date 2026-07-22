const CURRENT_PROGRAM_KEY = "gymsync.currentProgram";

export interface CurrentProgramRef {
  id: string;
  name: string;
  dayNumber?: number;
  dayName?: string;
  updatedAt: string;
}

export function getCurrentProgram(): CurrentProgramRef | null {
  try {
    const raw = localStorage.getItem(CURRENT_PROGRAM_KEY);
    if (!raw) return null;
    return JSON.parse(raw) as CurrentProgramRef;
  } catch {
    return null;
  }
}

export function setCurrentProgram(ref: Omit<CurrentProgramRef, "updatedAt">) {
  const next: CurrentProgramRef = { ...ref, updatedAt: new Date().toISOString() };
  localStorage.setItem(CURRENT_PROGRAM_KEY, JSON.stringify(next));
  window.dispatchEvent(new CustomEvent("gymsync:current-program", { detail: next }));
}

export function clearCurrentProgram() {
  localStorage.removeItem(CURRENT_PROGRAM_KEY);
  window.dispatchEvent(new CustomEvent("gymsync:current-program", { detail: null }));
}
