import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { Plus, X } from "lucide-react";
import { ApiError } from "../api/http";
import * as programsApi from "../api/programsApi";
import type { ProgramSummary } from "../api/types";
import { useToast } from "../components/Toast";
import { EmptyState, ErrorBanner, LoadingBlock, PageHeader } from "../components/ui";

export function ProgramsPage() {
  const { push } = useToast();
  const [programs, setPrograms] = useState<ProgramSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setPrograms(await programsApi.listPrograms());
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to load programs.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  async function onCreate(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await programsApi.createProgram(name.trim(), description.trim() || undefined);
      setName("");
      setDescription("");
      setCreateOpen(false);
      push("Program created", "success");
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not create program.");
    } finally {
      setBusy(false);
    }
  }

  async function onDelete(program: ProgramSummary) {
    setBusy(true);
    try {
      await programsApi.deleteProgram(program.id);
      push("Program deleted", "success");
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete program.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="stack">
      <PageHeader
        title="Programs"
        subtitle="Build simple training plans and start a day as guidance."
        actions={
          <button type="button" className="btn btn-primary" onClick={() => setCreateOpen(true)}>
            <Plus size={18} /> Create program
          </button>
        }
      />

      {error ? <ErrorBanner message={error} onRetry={() => void load()} /> : null}
      {loading ? <LoadingBlock /> : null}

      {!loading && programs.length === 0 ? (
        <EmptyState
          title="No programs yet"
          description="Create a plan, add days, then start a day into an active session."
          action={
            <button type="button" className="btn btn-primary" onClick={() => setCreateOpen(true)}>
              Create program
            </button>
          }
        />
      ) : null}

      {!loading && programs.length > 0 ? (
        <div className="card-grid">
          {programs.map((program) => (
            <article key={program.id} className="card card-feature card-accent-train stack" style={{ gap: "0.75rem" }}>
              <div>
                <Link
                  to={`/programs/${program.id}`}
                  style={{
                    textDecoration: "none",
                    color: "var(--color-text)",
                    fontFamily: "var(--font-display)",
                    fontSize: "1.25rem",
                    fontWeight: 700,
                  }}
                >
                  {program.name}
                </Link>
                <p className="muted" style={{ margin: "0.4rem 0 0", fontSize: "var(--text-sm)" }}>
                  {program.description || "No description"}
                </p>
              </div>
              <div className="row" style={{ justifyContent: "space-between" }}>
                <span className="badge badge-active">{program.dayCount} days</span>
                <div className="row">
                  <Link to={`/programs/${program.id}`} className="btn btn-secondary" style={{ minHeight: 36, padding: "0.35rem 0.7rem" }}>
                    Open
                  </Link>
                  <button
                    type="button"
                    className="btn btn-danger"
                    style={{ minHeight: 36, padding: "0.35rem 0.7rem" }}
                    disabled={busy}
                    onClick={() => void onDelete(program)}
                  >
                    Delete
                  </button>
                </div>
              </div>
            </article>
          ))}
        </div>
      ) : null}

      {createOpen ? (
        <div className="modal-backdrop" role="dialog" aria-modal="true">
          <form className="modal-panel stack" onSubmit={onCreate}>
            <div className="row" style={{ justifyContent: "space-between" }}>
              <h2>Create program</h2>
              <button type="button" className="btn btn-icon btn-ghost" aria-label="Close" onClick={() => setCreateOpen(false)}>
                <X size={18} />
              </button>
            </div>
            <div className="field">
              <label htmlFor="prog-name">Name</label>
              <input
                id="prog-name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
              />
            </div>
            <div className="field">
              <label htmlFor="prog-desc">Description</label>
              <textarea
                id="prog-desc"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                rows={3}
              />
            </div>
            <div className="row">
              <button type="submit" className="btn btn-primary" disabled={busy}>
                Create
              </button>
              <button type="button" className="btn btn-ghost" onClick={() => setCreateOpen(false)}>
                Cancel
              </button>
            </div>
          </form>
        </div>
      ) : null}
    </div>
  );
}
