import { Link } from "react-router-dom";
import type { CoachRecommendation } from "../api/types";
import { StatusBadge, formatDateTime } from "./ui";

function tryPrettyJson(raw: string): string {
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
}

export function RecommendationCard({
  recommendation,
  exerciseName,
  actions,
  compact = false,
}: {
  recommendation: CoachRecommendation;
  exerciseName?: string;
  actions?: React.ReactNode;
  compact?: boolean;
}) {
  const isSafety = recommendation.recommendationType === "SAFETY_HOLD";
  return (
    <article
      className={`card card-feature ${isSafety ? "card-accent-caution" : "card-accent-coach"} stack`}
      style={{ gap: compact ? "0.65rem" : "0.85rem" }}
    >
      <div className="row" style={{ justifyContent: "space-between" }}>
        <div className="row">
          <span className={`badge ${isSafety ? "badge-danger" : "badge-coach"}`}>
            {recommendation.recommendationType.replaceAll("_", " ")}
          </span>
          <StatusBadge status={recommendation.status} />
          <span className="badge">{recommendation.confidenceLevel}</span>
        </div>
        <span className="muted" style={{ fontSize: "var(--text-xs)" }}>
          {formatDateTime(recommendation.createdAt)}
        </span>
      </div>
      {exerciseName ? (
        <div style={{ fontWeight: 650 }}>{exerciseName}</div>
      ) : (
        <Link to={`/exercises/${recommendation.exerciseId}`} className="muted" style={{ fontSize: "var(--text-sm)" }}>
          View exercise →
        </Link>
      )}
      <p style={{ margin: 0 }}>{recommendation.reasoning}</p>
      {!compact ? (
        <div className="grid-2" style={{ gap: "0.65rem" }}>
          <div>
            <div className="muted" style={{ fontSize: "var(--text-xs)", marginBottom: "0.25rem" }}>
              Previous
            </div>
            <pre className="mono rec-pre">{tryPrettyJson(recommendation.previousPerformance)}</pre>
          </div>
          <div>
            <div className="muted" style={{ fontSize: "var(--text-xs)", marginBottom: "0.25rem" }}>
              Proposed
            </div>
            <pre className="mono rec-pre">{tryPrettyJson(recommendation.proposedChange)}</pre>
          </div>
        </div>
      ) : (
        <pre className="mono rec-pre">{tryPrettyJson(recommendation.proposedChange)}</pre>
      )}
      {actions}
    </article>
  );
}
