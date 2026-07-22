import { NavLink, useLocation, useNavigate } from "react-router-dom";
import { useEffect, useState, type ReactNode } from "react";
import {
  Activity,
  BarChart3,
  ClipboardList,
  Dumbbell,
  FileText,
  HeartPulse,
  LayoutDashboard,
  LogOut,
  Menu,
  Sparkles,
  TrendingUp,
  X,
} from "lucide-react";
import { useAuth } from "../auth/AuthContext";
import { initials } from "./ui";
import { useSessionSocket } from "../ws/SessionSocketContext";
import { ApiError } from "../api/http";
import * as workoutsApi from "../api/workoutsApi";
import { getCurrentProgram, type CurrentProgramRef } from "../lib/currentProgram";
import { useToast } from "./Toast";
import "./shell.css";

const TRAIN = [
  { to: "/", label: "Dashboard", icon: LayoutDashboard, end: true },
  { to: "/session", label: "Active Session", icon: Activity },
  { to: "/workouts", label: "Workouts", icon: ClipboardList },
  { to: "/programs", label: "Programs", icon: Dumbbell },
];

const ANALYZE = [
  { to: "/exercises", label: "Exercises", icon: Dumbbell },
  { to: "/strength", label: "Strength", icon: TrendingUp },
  { to: "/stats", label: "Stats", icon: BarChart3 },
  { to: "/import", label: "Import Notes", icon: FileText },
];

const COACHING = [
  { to: "/readiness", label: "Readiness", icon: HeartPulse },
  { to: "/coach", label: "AI Coach", icon: Sparkles },
];

const MOBILE_PRIMARY = [
  { to: "/", label: "Home", icon: LayoutDashboard, end: true },
  { to: "/session", label: "Session", icon: Activity },
  { to: "/exercises", label: "Exercises", icon: Dumbbell },
  { to: "/coach", label: "Coach", icon: Sparkles },
  { to: "/stats", label: "Stats", icon: BarChart3 },
];

function NavGroup({
  title,
  items,
  onNavigate,
}: {
  title: string;
  items: Array<{ to: string; label: string; icon: typeof LayoutDashboard; end?: boolean }>;
  onNavigate?: () => void;
}) {
  return (
    <div className="nav-group">
      <div className="nav-group-title">{title}</div>
      {items.map((item) => {
        const Icon = item.icon;
        return (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            className={({ isActive }) => `side-link${isActive ? " active" : ""}`}
            onClick={onNavigate}
          >
            <Icon size={18} aria-hidden />
            <span>{item.label}</span>
          </NavLink>
        );
      })}
    </div>
  );
}

export function AppShell({
  children,
  menuOpen,
  onToggleMenu,
}: {
  children: ReactNode;
  menuOpen: boolean;
  onToggleMenu: () => void;
}) {
  const { user, logout } = useAuth();
  const { connected } = useSessionSocket();
  const navigate = useNavigate();
  const location = useLocation();
  const { push } = useToast();
  const [currentProgram, setCurrentProgramState] = useState<CurrentProgramRef | null>(() =>
    getCurrentProgram(),
  );

  useEffect(() => {
    function refresh() {
      setCurrentProgramState(getCurrentProgram());
    }
    window.addEventListener("gymsync:current-program", refresh);
    window.addEventListener("storage", refresh);
    return () => {
      window.removeEventListener("gymsync:current-program", refresh);
      window.removeEventListener("storage", refresh);
    };
  }, []);

  async function startWorkout() {
    try {
      await workoutsApi.startWorkout();
      push("Workout started", "success");
      navigate("/session");
    } catch (err) {
      const message =
        err instanceof ApiError ? err.message : "Could not start workout.";
      push(message, "error");
      if (err instanceof ApiError && err.status === 409) {
        navigate("/session");
      }
    }
  }

  const navItems: Array<{ to: string; label: string; icon: typeof LayoutDashboard; end?: boolean }> = [
    ...TRAIN,
    ...ANALYZE,
    ...COACHING,
  ];
  const pageTitle =
    navItems.find((i) =>
      i.end ? location.pathname === i.to : location.pathname.startsWith(i.to) && i.to !== "/",
    )?.label ?? (location.pathname === "/" ? "Dashboard" : "GymSync");

  return (
    <div className={`app-shell${menuOpen ? " menu-open" : ""}`}>
      <aside className="sidebar" aria-label="Primary">
        <div className="brand">
          <div>
            <div className="brand-mark">GymSync</div>
            <div className="brand-sub muted">Adaptive strength</div>
          </div>
          <span
            className={`ws-dot${connected ? " on" : ""}`}
            title={connected ? "Live sync connected" : "Sync reconnecting"}
            aria-label={connected ? "Live sync on" : "Sync offline"}
          />
        </div>

        <button type="button" className="btn btn-primary start-workout-btn" onClick={() => void startWorkout()}>
          Start Workout
        </button>

        <nav className="side-nav">
          <NavGroup title="Train" items={TRAIN} onNavigate={() => menuOpen && onToggleMenu()} />
          <NavGroup title="Analyze" items={ANALYZE} onNavigate={() => menuOpen && onToggleMenu()} />
          <NavGroup title="Coaching" items={COACHING} onNavigate={() => menuOpen && onToggleMenu()} />
        </nav>

        <div className="side-footer">
          <div className="profile-card">
            <div className="avatar" aria-hidden>
              {initials(user?.name ?? "U")}
            </div>
            <div className="profile-meta">
              <div className="profile-name">{user?.name}</div>
              <div className="muted" style={{ fontSize: "var(--text-xs)" }}>
                {currentProgram
                  ? `${currentProgram.name}${
                      currentProgram.dayName ? ` · ${currentProgram.dayName}` : ""
                    }`
                  : user?.email}
              </div>
            </div>
          </div>
          <button type="button" className="btn btn-ghost sign-out-btn" onClick={logout}>
            <LogOut size={16} aria-hidden />
            Sign out
          </button>
        </div>
      </aside>

      {menuOpen ? (
        <button type="button" className="backdrop" aria-label="Close menu" onClick={onToggleMenu} />
      ) : null}

      <div className="main-column">
        <header className="topbar">
          <button
            type="button"
            className="btn btn-secondary menu-btn btn-icon"
            onClick={onToggleMenu}
            aria-label={menuOpen ? "Close menu" : "Open menu"}
          >
            {menuOpen ? <X size={18} /> : <Menu size={18} />}
          </button>
          <div className="topbar-title">
            <div className="topbar-page">{pageTitle}</div>
            <div className="muted" style={{ fontSize: "var(--text-xs)" }}>
              {connected ? "Live session sync" : "Reconnecting…"}
            </div>
          </div>
          <button type="button" className="btn btn-primary top-start" onClick={() => void startWorkout()}>
            Start
          </button>
        </header>
        <main className="content">{children}</main>
      </div>

      <nav className="bottom-nav" aria-label="Primary mobile">
        {MOBILE_PRIMARY.map((item) => {
          const Icon = item.icon;
          return (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) => `bottom-link${isActive ? " active" : ""}`}
            >
              <Icon size={20} aria-hidden />
              <span>{item.label}</span>
            </NavLink>
          );
        })}
      </nav>
    </div>
  );
}
