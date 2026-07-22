import type {
  CoachRecommendation,
  ExerciseCategory,
  PlannedExercise,
  SetType,
  WeightEntryMode,
  WorkoutSet,
} from "../api/types";
import type { SetPayload } from "../api/workoutsApi";

export const DEFAULT_BAR_WEIGHT = 45;

export const BARBELL_CATEGORIES: ExerciseCategory[] = [
  "CHEST",
  "BACK",
  "LEGS",
  "SHOULDERS",
  "FULL_BODY",
];

export const SET_TYPE_OPTIONS: SetType[] = [
  "WARMUP",
  "WORKING",
  "BACKOFF",
  "DROP",
  "FAILURE",
  "AMRAP",
];

export type PrefillSource = "coach" | "program" | "last_session" | "previous_set";

export type PrefillValues = {
  enteredWeight: string;
  reps: string;
  setType: SetType;
  rir: string;
  weightEntryMode: WeightEntryMode;
  barWeight: string;
  source: PrefillSource;
  sourceLabel: string;
};

export type ExerciseGroup = {
  exerciseId: string;
  exerciseName: string;
  exerciseCategory: ExerciseCategory;
  sets: WorkoutSet[];
};

export function toNum(value: number | string): number {
  return typeof value === "number" ? value : Number(value);
}

export function isBarbellCategory(category: ExerciseCategory): boolean {
  return BARBELL_CATEGORIES.includes(category);
}

export function groupSetsByExercise(sets: WorkoutSet[]): ExerciseGroup[] {
  const order: string[] = [];
  const map = new Map<string, ExerciseGroup>();

  for (const set of sets) {
    if (!map.has(set.exerciseId)) {
      order.push(set.exerciseId);
      map.set(set.exerciseId, {
        exerciseId: set.exerciseId,
        exerciseName: set.exerciseName,
        exerciseCategory: set.exerciseCategory,
        sets: [],
      });
    }
    map.get(set.exerciseId)!.sets.push(set);
  }

  for (const group of map.values()) {
    group.sets.sort((a, b) => a.setNumber - b.setNumber);
  }

  return order.map((id) => map.get(id)!);
}

export function countSetTypes(sets: WorkoutSet[]): { warmup: number; working: number } {
  let warmup = 0;
  let working = 0;
  for (const set of sets) {
    if (set.setType === "WARMUP") {
      warmup += 1;
    } else {
      working += 1;
    }
  }
  return { warmup, working };
}

export type StructuredCoachChange = {
  proposedWeight?: number;
  proposedRepRangeMin?: number;
  proposedRepRangeMax?: number;
  targetRir?: number;
};

export function parseCoachProposedChange(raw: string): StructuredCoachChange | null {
  try {
    const parsed = JSON.parse(raw) as Record<string, unknown>;
    const proposedWeight =
      parsed.proposedWeight != null ? toNum(parsed.proposedWeight as number | string) : undefined;
    const proposedRepRangeMin =
      parsed.proposedRepRangeMin != null
        ? Number(parsed.proposedRepRangeMin)
        : undefined;
    const proposedRepRangeMax =
      parsed.proposedRepRangeMax != null
        ? Number(parsed.proposedRepRangeMax)
        : undefined;
    const targetRir = parsed.targetRir != null ? Number(parsed.targetRir) : undefined;
    if (
      proposedWeight == null &&
      proposedRepRangeMin == null &&
      targetRir == null
    ) {
      return null;
    }
    return { proposedWeight, proposedRepRangeMin, proposedRepRangeMax, targetRir };
  } catch {
    return null;
  }
}

export function resolvePrefill(params: {
  exerciseId: string;
  category: ExerciseCategory;
  sessionSets: WorkoutSet[];
  planned: PlannedExercise | null;
  coachRec: CoachRecommendation | null;
  lastSession: { weight: number | string; reps: number } | null;
}): PrefillValues {
  const previousInSession = [...params.sessionSets]
    .filter((s) => s.exerciseId === params.exerciseId)
    .sort((a, b) => b.setNumber - a.setNumber)[0];

  if (previousInSession) {
    return fromSet(previousInSession, "previous_set", "from previous set");
  }

  if (params.coachRec) {
    const structured = parseCoachProposedChange(params.coachRec.proposedChange);
    if (structured?.proposedWeight != null) {
      return {
        enteredWeight: String(structured.proposedWeight),
        reps: String(structured.proposedRepRangeMin ?? structured.proposedRepRangeMax ?? 8),
        setType: "WORKING",
        rir: structured.targetRir != null ? String(structured.targetRir) : "",
        weightEntryMode: "TOTAL",
        barWeight: String(DEFAULT_BAR_WEIGHT),
        source: "coach",
        sourceLabel: "from coach rec",
      };
    }
  }

  if (params.planned?.targetWeight != null) {
    return {
      enteredWeight: String(params.planned.targetWeight),
      reps: String(params.planned.targetRepsMin ?? params.planned.targetRepsMax ?? 8),
      setType: "WORKING",
      rir: "",
      weightEntryMode: "TOTAL",
      barWeight: String(DEFAULT_BAR_WEIGHT),
      source: "program",
      sourceLabel: "from program planned",
    };
  }

  if (params.lastSession) {
    return {
      enteredWeight: String(params.lastSession.weight),
      reps: String(params.lastSession.reps),
      setType: "WORKING",
      rir: "",
      weightEntryMode: "TOTAL",
      barWeight: String(DEFAULT_BAR_WEIGHT),
      source: "last_session",
      sourceLabel: "from last session",
    };
  }

  return {
    enteredWeight: "135",
    reps: "8",
    setType: "WORKING",
    rir: "",
    weightEntryMode: "TOTAL",
    barWeight: String(DEFAULT_BAR_WEIGHT),
    source: "last_session",
    sourceLabel: "",
  };
}

function fromSet(
  set: WorkoutSet,
  source: PrefillSource,
  sourceLabel: string,
): PrefillValues {
  return {
    enteredWeight: String(set.enteredWeight),
    reps: String(set.reps),
    setType: set.setType,
    rir: set.rir != null ? String(set.rir) : "",
    weightEntryMode: set.weightEntryMode,
    barWeight: set.barWeight != null ? String(set.barWeight) : String(DEFAULT_BAR_WEIGHT),
    source,
    sourceLabel,
  };
}

export type BatchSetPayloadInput = {
  exerciseId: string;
  count: number;
  startingSetNumber: number;
  enteredWeight: number;
  weightEntryMode: WeightEntryMode;
  barWeight?: number;
  reps: number;
  setType?: SetType;
  rir?: number;
  note?: string;
};

export function buildBatchSetPayload(
  input: BatchSetPayloadInput,
): Omit<SetPayload, "setNumber"> & { count: number; startingSetNumber: number } {
  const payload: Omit<SetPayload, "setNumber"> & { count: number; startingSetNumber: number } = {
    exerciseId: input.exerciseId,
    count: input.count,
    startingSetNumber: input.startingSetNumber,
    enteredWeight: input.enteredWeight,
    weightEntryMode: input.weightEntryMode,
    reps: input.reps,
    setType: input.setType ?? "WORKING",
  };

  if (input.weightEntryMode === "PER_SIDE") {
    payload.barWeight = input.barWeight ?? DEFAULT_BAR_WEIGHT;
  }

  if (input.rir != null && !Number.isNaN(input.rir)) {
    payload.rir = input.rir;
  }

  if (input.note?.trim()) {
    payload.note = input.note.trim();
  }

  return payload;
}

export function formatWeightDisplay(set: WorkoutSet): string {
  if (set.weightEntryMode === "PER_SIDE") {
    const side = toNum(set.enteredWeight);
    const bar = set.barWeight != null ? toNum(set.barWeight) : DEFAULT_BAR_WEIGHT;
    return `${side}/side + ${bar} bar`;
  }
  return String(set.enteredWeight);
}
