export type Role = "USER" | "ADMIN";

export type WorkoutStatus = "ACTIVE" | "COMPLETED" | "CANCELLED";
export type WorkoutSource = "MANUAL" | "WEBHOOK" | "NOTES_IMPORT";
export type ImportStatus = "NOT_APPLICABLE" | "PENDING_REVIEW" | "CONFIRMED" | "REJECTED";

export type ExerciseCategory =
  | "CHEST"
  | "BACK"
  | "LEGS"
  | "ARMS"
  | "SHOULDERS"
  | "CORE"
  | "CARDIO"
  | "FULL_BODY"
  | "OTHER";

export interface User {
  id: string;
  email: string;
  name: string;
  role: Role;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: User;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface ApiErrorBody {
  timestamp?: string;
  status: number;
  error?: string;
  code?: string;
  message: string;
  path?: string;
  fieldErrors?: Record<string, string>;
  correlationId?: string;
}

export interface Exercise {
  id: string;
  name: string;
  category: ExerciseCategory;
  global: boolean;
  owned: boolean;
  thumbnailUrl: string | null;
  demoVideoUrl: string | null;
  mediaAttribution: string | null;
  createdAt: string;
  updatedAt: string;
}

export type MediaSuggestionSource = "CURATED" | "WIKIMEDIA" | "YOUTUBE_API";
export type MediaSuggestionConfidence = "HIGH" | "MEDIUM" | "LOW";
export type MediaSuggestionStatus = "PENDING" | "CONFIRMED" | "REJECTED";

export interface ExerciseMediaSuggestion {
  id: string;
  exerciseId: string;
  thumbnailUrl: string | null;
  demoVideoUrl: string | null;
  mediaAttribution: string;
  source: MediaSuggestionSource;
  confidence: MediaSuggestionConfidence;
  status: MediaSuggestionStatus;
  createdAt: string;
  reviewedAt: string | null;
}

export interface Workout {
  id: string;
  startedAt: string;
  endedAt: string | null;
  status: WorkoutStatus;
  source: WorkoutSource;
  importStatus: ImportStatus;
  createdAt: string;
  updatedAt: string;
}

export type SetType = "WARMUP" | "WORKING" | "BACKOFF" | "DROP" | "FAILURE" | "AMRAP";
export type WeightEntryMode = "TOTAL" | "PER_SIDE";

export interface PrDetectionResult {
  weightPr: boolean;
  estimatedOneRepMaxPr: boolean;
  previousBestWeight: number | string | null;
  previousBestEstimatedOneRepMax: number | string | null;
}

export interface WorkoutSet {
  id: string;
  exerciseId: string;
  exerciseName: string;
  exerciseCategory: ExerciseCategory;
  weight: number | string;
  reps: number;
  setNumber: number;
  loggedAt: string;
  setType: SetType;
  rir: number | null;
  weightEntryMode: WeightEntryMode;
  barWeight: number | string | null;
  enteredWeight: number | string;
  note: string | null;
  completed: boolean;
  createdAt: string;
  updatedAt: string;
  prEvent?: PrDetectionResult | null;
}

export interface WorkoutExerciseNote {
  exerciseId: string;
  note: string;
}

export interface WorkoutDetail extends Workout {
  sets: WorkoutSet[];
}

export interface WeeklyStats {
  weekStart: string;
  weekEnd: string;
  completedWorkouts: number;
  totalSets: number;
  totalReps: number;
  totalVolume: number | string;
  volumeByExercise: Array<{ exerciseId: string; exerciseName: string; volume: number | string }>;
  volumeByCategory: Array<{ category: ExerciseCategory; volume: number | string }>;
  comparisonWithPriorWeek: {
    priorWeekVolume: number | string;
    volumeChange: number | string;
    volumeChangePercent: number | null;
  };
}

export interface PersonalRecord {
  exerciseId: string;
  heaviestWeight: number | string;
  repsAtHeaviestWeight: number;
  highestRepsAtHeaviestWeight: number;
  heaviestWeightAchievedAt: string;
  heaviestWeightWorkoutId: string;
  estimatedOneRepMax: number | string;
  estimatedOneRepMaxAchievedAt: string;
  estimatedOneRepMaxWorkoutId: string;
  disclaimer: string;
}

export interface VolumeTrendPoint {
  weekStart: string;
  weekEnd: string;
  totalVolume: number | string;
}

export interface VolumeTrend {
  weeks: number;
  points: VolumeTrendPoint[];
}

export interface Estimate1RmResponse {
  exerciseName: string | null;
  epley: number | string;
  brzycki: number | string;
  workingEstimate: number | string;
  rangeMin: number | string;
  rangeMax: number | string;
  lowConfidence: boolean;
  rirContext: number | null;
  confidenceNote: string;
}

export interface CoachApplyPreview {
  workoutId: string;
  exerciseId: string;
  sets: Array<{
    setId: string;
    setNumber: number;
    completed: boolean;
    willChange: boolean;
    currentWeight: number | string;
    currentReps: number | null;
    currentRir: number | null;
    proposedWeight: number | string | null;
    proposedReps: number | null;
    proposedRir: number | null;
  }>;
}

export interface PrHistoryPoint {
  at: string;
  workoutId: string;
  weight: number | string;
  reps: number;
  estimatedOneRepMax: number | string;
}

export interface PrHistory {
  exerciseId: string;
  points: PrHistoryPoint[];
  disclaimer: string;
}

export type RecommendationType =
  | "WEIGHT_ADJUSTMENT"
  | "VOLUME_ADJUSTMENT"
  | "DELOAD"
  | "GENERAL"
  | "SAFETY_HOLD";

export type ConfidenceLevel = "LOW" | "MEDIUM" | "HIGH";
export type RecommendationStatus = "PROPOSED" | "ACCEPTED" | "MODIFIED" | "DISMISSED";

export interface CoachRecommendation {
  id: string;
  exerciseId: string | null;
  basedOnWorkoutId: string | null;
  readinessCheckInId: string | null;
  recommendationType: RecommendationType;
  previousPerformance: string;
  proposedChange: string;
  reasoning: string;
  confidenceLevel: ConfidenceLevel;
  status: RecommendationStatus;
  createdAt: string;
  respondedAt: string | null;
}

export interface ReadinessCheckIn {
  id: string;
  workoutId: string | null;
  sleepHours: number | string | null;
  sleepQuality: number | null;
  energyLevel: number | null;
  soreness: number | null;
  stress: number | null;
  note: string | null;
  createdAt: string;
}

export interface ExerciseDetail {
  exercise: Exercise;
  personalRecord: PersonalRecord | null;
  prHistory: PrHistory;
  recentSets: Array<{
    setId: string;
    workoutId: string;
    loggedAt: string;
    weight: number | string;
    reps: number;
    setNumber: number;
  }>;
  activeRecommendation: CoachRecommendation | null;
}

export interface ProgramSummary {
  id: string;
  name: string;
  description: string | null;
  dayCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface PlannedExercise {
  id: string;
  exerciseId: string;
  exerciseName: string;
  orderInDay: number;
  targetSets: number;
  targetRepsMin: number | null;
  targetRepsMax: number | null;
  targetWeight: number | string | null;
}

export interface ProgramDay {
  id: string;
  dayNumber: number;
  name: string;
  exercises: PlannedExercise[];
}

export interface ProgramDetail {
  id: string;
  name: string;
  description: string | null;
  days: ProgramDay[];
  createdAt: string;
  updatedAt: string;
}

export interface StartProgramDayResponse {
  workout: Workout;
  plannedExercises: PlannedExercise[];
}

export interface NextSessionRecommendation {
  exerciseId: string;
  basedOnWorkoutId: string;
  previousPerformance: { weight: number | string; reps: number };
  recommendation: {
    optionA: { weight: number | string; targetReps: number };
    optionB: { weight: number | string; targetReps: number };
  };
  reason: string;
  disclaimer: string;
}

export type SessionEventType =
  | "SESSION_STARTED"
  | "SET_LOGGED"
  | "SET_UPDATED"
  | "SET_DELETED"
  | "SESSION_COMPLETED";

export interface SessionEvent {
  eventId: string;
  eventType: SessionEventType;
  occurredAt: string;
  workoutId: string;
  data: Record<string, unknown>;
}

export const EXERCISE_CATEGORIES: ExerciseCategory[] = [
  "CHEST",
  "BACK",
  "LEGS",
  "ARMS",
  "SHOULDERS",
  "CORE",
  "CARDIO",
  "FULL_BODY",
  "OTHER",
];
