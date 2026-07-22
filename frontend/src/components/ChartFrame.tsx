import type { ReactNode } from "react";

export function ChartFrame({
  title,
  subtitle,
  actions,
  summary,
  children,
  empty,
}: {
  title: string;
  subtitle?: string;
  actions?: ReactNode;
  /** Screen-reader summary of the chart data */
  summary?: string;
  children?: ReactNode;
  empty?: ReactNode;
}) {
  return (
    <section className="card card-feature card-accent-train chart-frame">
      <div className="row" style={{ justifyContent: "space-between", alignItems: "flex-start" }}>
        <div>
          <h2>{title}</h2>
          {subtitle ? (
            <p className="muted" style={{ margin: "0.35rem 0 0", fontSize: "var(--text-sm)" }}>
              {subtitle}
            </p>
          ) : null}
        </div>
        {actions}
      </div>
      {summary ? (
        <p className="sr-only">{summary}</p>
      ) : null}
      <div className="chart-frame-body">{empty ?? children}</div>
    </section>
  );
}
