import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import type { SetType } from "../api/types";

const DEFAULT_SECONDS: Record<SetType, number> = {
  WARMUP: 45,
  WORKING: 90,
  BACKOFF: 75,
  DROP: 60,
  FAILURE: 120,
  AMRAP: 120,
};

type SessionRestTimerContextValue = {
  restEndsAt: number | null;
  restRemaining: number;
  startRest: (setType?: SetType) => void;
  addRestSeconds: (seconds: number) => void;
  skipRest: () => void;
  tick: () => void;
};

const SessionRestTimerContext = createContext<SessionRestTimerContextValue | null>(null);

export function SessionRestTimerProvider({ children }: { children: ReactNode }) {
  const [restEndsAt, setRestEndsAt] = useState<number | null>(null);
  const [now, setNow] = useState(Date.now());

  const tick = useCallback(() => setNow(Date.now()), []);

  const restRemaining = useMemo(() => {
    if (restEndsAt == null) return 0;
    return Math.max(0, Math.ceil((restEndsAt - now) / 1000));
  }, [restEndsAt, now]);

  const startRest = useCallback((setType: SetType = "WORKING") => {
    const seconds = DEFAULT_SECONDS[setType] ?? DEFAULT_SECONDS.WORKING;
    setRestEndsAt(Date.now() + seconds * 1000);
    setNow(Date.now());
  }, []);

  const addRestSeconds = useCallback((seconds: number) => {
    setRestEndsAt((current) => (current == null ? Date.now() + seconds * 1000 : current + seconds * 1000));
    setNow(Date.now());
  }, []);

  const skipRest = useCallback(() => {
    setRestEndsAt(null);
    setNow(Date.now());
  }, []);

  const value = useMemo(
    () => ({ restEndsAt, restRemaining, startRest, addRestSeconds, skipRest, tick }),
    [restEndsAt, restRemaining, startRest, addRestSeconds, skipRest, tick],
  );

  return (
    <SessionRestTimerContext.Provider value={value}>{children}</SessionRestTimerContext.Provider>
  );
}

export function useSessionRestTimer() {
  const ctx = useContext(SessionRestTimerContext);
  if (!ctx) {
    throw new Error("useSessionRestTimer must be used within SessionRestTimerProvider");
  }
  return ctx;
}
