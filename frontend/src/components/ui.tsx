import type { ReactNode } from "react";
import { Link } from "react-router-dom";

export function LoadingBlock({ label = "Loading…" }: { label?: string }) {
  return (
    <div className="loading-block" role="status">
      {label}
    </div>
  );
}

export function Skeleton({ label = "Loading…" }: { label?: string }) {
  return <div className="skeleton" role="status" aria-label={label} />;
}

export function EmptyState({
  title,
  description,
  action,
}: {
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="empty-state stack" style={{ gap: "0.65rem" }}>
      <strong style={{ color: "var(--color-text)", fontFamily: "var(--font-display)" }}>{title}</strong>
      {description ? <p className="muted" style={{ margin: 0 }}>{description}</p> : null}
      {action}
    </div>
  );
}

export function ErrorBanner({
  message,
  onRetry,
  backTo,
  backLabel = "Go back",
}: {
  message: string;
  onRetry?: () => void;
  backTo?: string;
  backLabel?: string;
}) {
  return (
    <div className="error-banner stack" role="alert" style={{ gap: "0.75rem" }}>
      <div>
        <strong>Something went wrong</strong>
        <p style={{ margin: "0.35rem 0 0" }}>{message}</p>
      </div>
      <div className="row">
        {onRetry ? (
          <button type="button" className="btn btn-secondary" onClick={onRetry}>
            Retry
          </button>
        ) : null}
        {backTo ? (
          <Link to={backTo} className="btn btn-ghost">
            {backLabel}
          </Link>
        ) : null}
      </div>
    </div>
  );
}

export function LivePulseBar({ live }: { live: boolean }) {
  return <div className={`live-pulse${live ? " is-live" : ""}`} aria-hidden />;
}

export function StatusBadge({
  status,
}: {
  status: string;
}) {
  const tone =
    status === "ACTIVE" || status === "PROPOSED"
      ? "badge-active"
      : status === "COMPLETED" || status === "CONFIRMED" || status === "ACCEPTED"
        ? "badge-success"
        : status === "CANCELLED" || status === "REJECTED" || status === "DISMISSED"
          ? "badge-danger"
          : status === "PENDING_REVIEW" || status === "PENDING" || status === "MODIFIED"
            ? "badge-warn"
            : status === "SAFETY_HOLD"
              ? "badge-danger"
              : "";
  return <span className={`badge ${tone}`.trim()}>{status.replaceAll("_", " ")}</span>;
}

export function PageHeader({
  title,
  subtitle,
  actions,
}: {
  title: string;
  subtitle?: string;
  actions?: ReactNode;
}) {
  return (
    <div className="page-header">
      <div>
        <h1>{title}</h1>
        {subtitle ? <p className="muted">{subtitle}</p> : null}
      </div>
      {actions ? <div className="row">{actions}</div> : null}
    </div>
  );
}

export function MetricCard({
  label,
  value,
  hint,
  accent = "train",
  children,
}: {
  label: string;
  value: ReactNode;
  hint?: string;
  accent?: "train" | "progress" | "caution" | "coach";
  children?: ReactNode;
}) {
  return (
    <div className={`card card-feature card-accent-${accent}`}>
      <div className="metric">{value}</div>
      <div className="metric-label">{label}</div>
      {hint ? <p className="muted" style={{ margin: "0.5rem 0 0", fontSize: "var(--text-sm)" }}>{hint}</p> : null}
      {children}
    </div>
  );
}

export function ProgressRing({
  value,
  max = 100,
  size = 88,
  stroke = 8,
  color = "var(--color-train)",
  label,
}: {
  value: number;
  max?: number;
  size?: number;
  stroke?: number;
  color?: string;
  label?: string;
}) {
  const radius = (size - stroke) / 2;
  const circumference = 2 * Math.PI * radius;
  const pct = max <= 0 ? 0 : Math.min(1, Math.max(0, value / max));
  const offset = circumference * (1 - pct);
  return (
    <div className="progress-ring" style={{ width: size, height: size, position: "relative" }}>
      <svg width={size} height={size} aria-hidden>
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke="var(--color-border)"
          strokeWidth={stroke}
        />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke={color}
          strokeWidth={stroke}
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={offset}
        />
      </svg>
      {label ? (
        <div
          className="ring-label"
          style={{ inset: 0, position: "absolute", display: "grid", placeItems: "center", fontSize: "1.1rem" }}
        >
          {label}
        </div>
      ) : null}
    </div>
  );
}

export function formatNumber(value: number | string | null | undefined, digits = 0): string {
  if (value === null || value === undefined || value === "") {
    return "—";
  }
  const n = typeof value === "number" ? value : Number(value);
  if (Number.isNaN(n)) {
    return String(value);
  }
  return n.toLocaleString(undefined, {
    maximumFractionDigits: digits,
    minimumFractionDigits: digits,
  });
}

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}

export function formatDuration(startedAt: string, endedAt?: string | null): string {
  const start = new Date(startedAt).getTime();
  const end = endedAt ? new Date(endedAt).getTime() : Date.now();
  const mins = Math.max(0, Math.round((end - start) / 60000));
  if (mins < 60) {
    return `${mins}m`;
  }
  const h = Math.floor(mins / 60);
  const m = mins % 60;
  return `${h}h ${m}m`;
}

export function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase() ?? "")
    .join("");
}
