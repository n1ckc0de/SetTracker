import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { ApiError } from "../api/http";
import * as readinessApi from "../api/readinessApi";
import type { ReadinessCheckIn } from "../api/types";
import { ChartFrame } from "../components/ChartFrame";
import { useToast } from "../components/Toast";
import {
  EmptyState,
  ErrorBanner,
  LoadingBlock,
  MetricCard,
  PageHeader,
  formatDateTime,
} from "../components/ui";

function ScaleCard({
  id,
  label,
  value,
  onChange,
  hint,
  tone = "",
}: {
  id: string;
  label: string;
  value: number;
  onChange: (v: number) => void;
  hint: string;
  tone?: string;
}) {
  return (
    <div className={`slider-card ${tone}`.trim()}>
      <div className="row" style={{ justifyContent: "space-between", marginBottom: "0.5rem" }}>
        <label htmlFor={id} style={{ color: "var(--color-text)", fontWeight: 650 }}>
          {label}
        </label>
        <span className="mono" style={{ fontSize: "1.25rem", fontWeight: 700 }}>
          {value}
        </span>
      </div>
      <input
        id={id}
        type="range"
        min={1}
        max={5}
        step={1}
        value={value}
        onChange={(e) => onChange(Number(e.target.value))}
      />
      <p className="muted" style={{ margin: "0.45rem 0 0", fontSize: "var(--text-sm)" }}>
        {hint}
      </p>
    </div>
  );
}

function estimateReadiness(input: {
  sleepQuality: number;
  energyLevel: number;
  soreness: number;
  stress: number;
  sleepHours: number | null;
}): { pct: number; label: string } {
  const invertedSoreness = 6 - input.soreness;
  const invertedStress = 6 - input.stress;
  let score =
    (input.sleepQuality + input.energyLevel + invertedSoreness + invertedStress) / 4;
  if (input.sleepHours != null) {
    const sleepFactor = Math.min(5, Math.max(1, input.sleepHours / 1.6));
    score = (score * 4 + sleepFactor) / 5;
  }
  const pct = Math.round(score * 20);
  let label = "Moderate";
  if (pct >= 80) label = "High";
  else if (pct >= 60) label = "Good";
  else if (pct < 45) label = "Low";
  return { pct, label };
}

export function ReadinessPage() {
  const { push } = useToast();
  const [latest, setLatest] = useState<ReadinessCheckIn | null>(null);
  const [history, setHistory] = useState<ReadinessCheckIn[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const [sleepHours, setSleepHours] = useState("7.5");
  const [sleepQuality, setSleepQuality] = useState(3);
  const [energyLevel, setEnergyLevel] = useState(3);
  const [soreness, setSoreness] = useState(2);
  const [stress, setStress] = useState(2);
  const [note, setNote] = useState("");

  const estimate = useMemo(
    () =>
      estimateReadiness({
        sleepQuality,
        energyLevel,
        soreness,
        stress,
        sleepHours: sleepHours === "" ? null : Number(sleepHours),
      }),
    [sleepQuality, energyLevel, soreness, stress, sleepHours],
  );

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const list = await readinessApi.listReadiness();
      setHistory(list);
      try {
        setLatest(await readinessApi.latestReadiness());
      } catch (err) {
        if (err instanceof ApiError && err.status === 404) {
          setLatest(null);
        } else {
          throw err;
        }
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to load readiness.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const created = await readinessApi.createReadiness({
        sleepHours: sleepHours === "" ? null : Number(sleepHours),
        sleepQuality,
        energyLevel,
        soreness,
        stress,
        note: note.trim() || null,
      });
      setLatest(created);
      setNote("");
      push("Check-in saved", "success");
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not save check-in.");
    } finally {
      setBusy(false);
    }
  }

  if (loading) {
    return <LoadingBlock label="Loading readiness…" />;
  }

  return (
    <div className="stack">
      <PageHeader
        title="Readiness"
        subtitle="Pre-workout check-in — sleep, energy, soreness, and stress."
        actions={
          <Link className="btn btn-secondary" to="/">
            Dashboard
          </Link>
        }
      />

      {error ? <ErrorBanner message={error} onRetry={() => void load()} /> : null}

      <section className="grid-2">
        <MetricCard
          label="Self-reported estimate"
          accent="caution"
          value={`${estimate.pct}%`}
          hint={`${estimate.label} — derived client-side from your slider averages (not a medical score).`}
        />
        {latest ? (
          <MetricCard
            label="Latest check-in"
            accent="progress"
            value={`Energy ${latest.energyLevel ?? "—"}`}
            hint={`${formatDateTime(latest.createdAt)} · Sleep ${latest.sleepHours ?? "—"}h · Soreness ${latest.soreness ?? "—"}`}
          />
        ) : (
          <div className="card">
            <EmptyState title="No check-ins yet" description="Submit one below to get started." />
          </div>
        )}
      </section>

      <form className="card stack" onSubmit={onSubmit}>
        <h2>New check-in</h2>
        <div className="field">
          <label htmlFor="sleep-hours">Sleep hours</label>
          <input
            id="sleep-hours"
            className="mono"
            type="number"
            min={0}
            max={24}
            step="0.25"
            value={sleepHours}
            onChange={(e) => setSleepHours(e.target.value)}
          />
        </div>
        <div className="grid-2">
          <ScaleCard
            id="sq"
            label="Sleep quality"
            value={sleepQuality}
            onChange={setSleepQuality}
            hint="1 poor · 5 excellent"
          />
          <ScaleCard
            id="en"
            label="Energy"
            value={energyLevel}
            onChange={setEnergyLevel}
            hint="1 drained · 5 sharp"
            tone="energy"
          />
          <ScaleCard
            id="so"
            label="Soreness"
            value={soreness}
            onChange={setSoreness}
            hint="1 fresh · 5 very sore"
            tone="soreness"
          />
          <ScaleCard
            id="st"
            label="Stress"
            value={stress}
            onChange={setStress}
            hint="1 calm · 5 high"
            tone="stress"
          />
        </div>
        <div className="field">
          <label htmlFor="note">Note</label>
          <textarea
            id="note"
            value={note}
            onChange={(e) => setNote(e.target.value)}
            maxLength={500}
            rows={3}
            placeholder="Optional — how you feel today"
          />
        </div>
        <button type="submit" className="btn btn-primary" disabled={busy}>
          {busy ? "Saving…" : "Save check-in"}
        </button>
      </form>

      <section className="card stack">
        <h2>History</h2>
        {history.length === 0 ? (
          <EmptyState title="No history" description="Your check-ins will appear on this timeline." />
        ) : (
          <>
            <ChartFrame
              title="Energy trend"
              subtitle="Self-reported energy (1–5) over recent check-ins"
              summary={`Energy across ${Math.min(history.length, 12)} check-ins.`}
            >
              <div style={{ width: "100%", height: 200 }}>
                <ResponsiveContainer>
                  <LineChart
                    data={[...history]
                      .slice(0, 12)
                      .reverse()
                      .map((row) => ({
                        at: new Date(row.createdAt).toLocaleDateString(undefined, {
                          month: "short",
                          day: "numeric",
                        }),
                        energy: row.energyLevel ?? null,
                        soreness: row.soreness ?? null,
                      }))}
                  >
                    <CartesianGrid stroke="var(--color-border)" strokeDasharray="3 3" />
                    <XAxis dataKey="at" stroke="var(--color-text-muted)" fontSize={11} />
                    <YAxis domain={[1, 5]} stroke="var(--color-text-muted)" fontSize={11} />
                    <Tooltip
                      contentStyle={{
                        background: "var(--color-surface-raised)",
                        border: "1px solid var(--color-border)",
                        borderRadius: 8,
                      }}
                    />
                    <Line
                      type="monotone"
                      dataKey="energy"
                      stroke="var(--color-progress)"
                      strokeWidth={2}
                      connectNulls
                    />
                    <Line
                      type="monotone"
                      dataKey="soreness"
                      stroke="var(--color-caution)"
                      strokeWidth={2}
                      connectNulls
                    />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            </ChartFrame>
            <div className="timeline">
              {history.slice(0, 12).map((row) => (
                <div key={row.id} className="timeline-item">
                  <div className="row" style={{ justifyContent: "space-between" }}>
                    <strong className="mono">{formatDateTime(row.createdAt)}</strong>
                    <span className="muted" style={{ fontSize: "var(--text-sm)" }}>
                      Energy {row.energyLevel ?? "—"} · Soreness {row.soreness ?? "—"} · Stress{" "}
                      {row.stress ?? "—"}
                    </span>
                  </div>
                  <p className="muted" style={{ margin: "0.35rem 0 0", fontSize: "var(--text-sm)" }}>
                    Sleep {row.sleepHours ?? "—"}h · Quality {row.sleepQuality ?? "—"}
                    {row.note ? ` · ${row.note}` : ""}
                  </p>
                </div>
              ))}
            </div>
          </>
        )}
      </section>
    </div>
  );
}
