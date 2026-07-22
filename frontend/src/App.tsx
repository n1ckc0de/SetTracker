import { Suspense, lazy, useState } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { useAuth } from "./auth/AuthContext";
import { AppShell } from "./components/AppShell";
import { LoadingBlock } from "./components/ui";
import { AuthPage } from "./pages/AuthPage";
import { SessionRestTimerProvider } from "./context/SessionRestTimerContext";
import { SessionSocketProvider } from "./ws/SessionSocketContext";

const DashboardPage = lazy(() =>
  import("./pages/DashboardPage").then((m) => ({ default: m.DashboardPage })),
);
const WorkoutsPage = lazy(() =>
  import("./pages/WorkoutsPage").then((m) => ({ default: m.WorkoutsPage })),
);
const WorkoutDetailPage = lazy(() =>
  import("./pages/WorkoutDetailPage").then((m) => ({ default: m.WorkoutDetailPage })),
);
const ActiveSessionPage = lazy(() =>
  import("./pages/ActiveSessionPage").then((m) => ({ default: m.ActiveSessionPage })),
);
const ExercisesPage = lazy(() =>
  import("./pages/ExercisesPage").then((m) => ({ default: m.ExercisesPage })),
);
const ExerciseDetailPage = lazy(() =>
  import("./pages/ExerciseDetailPage").then((m) => ({ default: m.ExerciseDetailPage })),
);
const ProgramsPage = lazy(() =>
  import("./pages/ProgramsPage").then((m) => ({ default: m.ProgramsPage })),
);
const ProgramDetailPage = lazy(() =>
  import("./pages/ProgramDetailPage").then((m) => ({ default: m.ProgramDetailPage })),
);
const ReadinessPage = lazy(() =>
  import("./pages/ReadinessPage").then((m) => ({ default: m.ReadinessPage })),
);
const CoachPage = lazy(() => import("./pages/CoachPage").then((m) => ({ default: m.CoachPage })));
const ImportNotesPage = lazy(() =>
  import("./pages/ImportNotesPage").then((m) => ({ default: m.ImportNotesPage })),
);
const StatsPage = lazy(() => import("./pages/StatsPage").then((m) => ({ default: m.StatsPage })));
const StrengthPage = lazy(() =>
  import("./pages/StrengthPage").then((m) => ({ default: m.StrengthPage })),
);

function PageFallback() {
  return (
    <div style={{ padding: "1rem 0" }}>
      <LoadingBlock label="Loading page…" />
    </div>
  );
}

function ProtectedApp() {
  const [menuOpen, setMenuOpen] = useState(false);
  return (
    <SessionSocketProvider>
      <SessionRestTimerProvider>
        <AppShell menuOpen={menuOpen} onToggleMenu={() => setMenuOpen((v) => !v)}>
          <Suspense fallback={<PageFallback />}>
            <Routes>
              <Route path="/" element={<DashboardPage />} />
              <Route path="/workouts" element={<WorkoutsPage />} />
              <Route path="/workouts/:workoutId" element={<WorkoutDetailPage />} />
              <Route path="/session" element={<ActiveSessionPage />} />
              <Route path="/exercises" element={<ExercisesPage />} />
              <Route path="/exercises/:exerciseId" element={<ExerciseDetailPage />} />
              <Route path="/programs" element={<ProgramsPage />} />
              <Route path="/programs/:programId" element={<ProgramDetailPage />} />
              <Route path="/readiness" element={<ReadinessPage />} />
              <Route path="/coach" element={<CoachPage />} />
              <Route path="/import" element={<ImportNotesPage />} />
              <Route path="/stats" element={<StatsPage />} />
              <Route path="/strength" element={<StrengthPage />} />
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </Suspense>
        </AppShell>
      </SessionRestTimerProvider>
    </SessionSocketProvider>
  );
}

export default function App() {
  const { user, ready } = useAuth();

  if (!ready) {
    return (
      <div style={{ padding: "2rem" }}>
        <LoadingBlock label="Starting GymSync…" />
      </div>
    );
  }

  if (!user) {
    return (
      <Routes>
        <Route path="/login" element={<AuthPage />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    );
  }

  return <ProtectedApp />;
}
