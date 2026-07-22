import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { Plus, X } from "lucide-react";
import { ApiError } from "../api/http";
import * as exercisesApi from "../api/exercisesApi";
import { EXERCISE_CATEGORIES, type Exercise, type ExerciseCategory } from "../api/types";
import { ExerciseThumbnail } from "../components/ExerciseMedia";
import { useToast } from "../components/Toast";
import { EmptyState, ErrorBanner, LoadingBlock, PageHeader } from "../components/ui";

type MediaForm = {
  thumbnailUrl: string;
  demoVideoUrl: string;
  mediaAttribution: string;
};

const emptyMedia = (): MediaForm => ({
  thumbnailUrl: "",
  demoVideoUrl: "",
  mediaAttribution: "",
});

export function ExercisesPage() {
  const { push } = useToast();
  const [query, setQuery] = useState("");
  const [category, setCategory] = useState<ExerciseCategory | "">("");
  const [page, setPage] = useState(0);
  const [items, setItems] = useState<Exercise[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [createOpen, setCreateOpen] = useState(false);
  const [name, setName] = useState("");
  const [newCategory, setNewCategory] = useState<ExerciseCategory>("OTHER");
  const [media, setMedia] = useState<MediaForm>(emptyMedia());
  const [editing, setEditing] = useState<Exercise | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await exercisesApi.listExercises({
        query: query.trim() || undefined,
        category,
        page,
        size: 15,
      });
      setItems(result.content);
      setTotalPages(result.totalPages);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to load exercises.");
    } finally {
      setLoading(false);
    }
  }, [query, category, page]);

  useEffect(() => {
    void load();
  }, [load]);

  function validateMedia(demoVideoUrl: string, mediaAttribution: string): string | null {
    if (demoVideoUrl.trim() && !mediaAttribution.trim()) {
      return "Media attribution is required when a demo video URL is set.";
    }
    return null;
  }

  async function onCreate(event: FormEvent) {
    event.preventDefault();
    const mediaError = validateMedia(media.demoVideoUrl, media.mediaAttribution);
    if (mediaError) {
      setError(mediaError);
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await exercisesApi.createExercise(name.trim(), newCategory, {
        thumbnailUrl: media.thumbnailUrl.trim() || undefined,
        demoVideoUrl: media.demoVideoUrl.trim() || undefined,
        mediaAttribution: media.mediaAttribution.trim() || undefined,
      });
      setName("");
      setMedia(emptyMedia());
      setCreateOpen(false);
      setPage(0);
      push("Exercise created", "success");
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not create exercise.");
    } finally {
      setBusy(false);
    }
  }

  async function onSaveEdit(event: FormEvent) {
    event.preventDefault();
    if (!editing) return;
    const mediaError = validateMedia(editing.demoVideoUrl ?? "", editing.mediaAttribution ?? "");
    if (mediaError) {
      setError(mediaError);
      return;
    }
    setBusy(true);
    try {
      await exercisesApi.updateExercise(editing.id, editing.name, editing.category, {
        thumbnailUrl: editing.thumbnailUrl?.trim() || undefined,
        demoVideoUrl: editing.demoVideoUrl?.trim() || undefined,
        mediaAttribution: editing.mediaAttribution?.trim() || undefined,
      });
      setEditing(null);
      push("Exercise updated", "success");
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update exercise.");
    } finally {
      setBusy(false);
    }
  }

  async function onDelete(exercise: Exercise) {
    if (!exercise.owned) return;
    setBusy(true);
    try {
      await exercisesApi.deleteExercise(exercise.id);
      push("Exercise deleted", "success");
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete exercise.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="stack">
      <PageHeader
        title="Exercises"
        subtitle="Global catalog plus your custom movements."
        actions={
          <button type="button" className="btn btn-primary" onClick={() => setCreateOpen(true)}>
            <Plus size={18} /> Create
          </button>
        }
      />

      <div className="card stack" style={{ gap: "0.85rem" }}>
        <div className="field">
          <label htmlFor="query">Search</label>
          <input
            id="query"
            value={query}
            onChange={(e) => {
              setPage(0);
              setQuery(e.target.value);
            }}
            placeholder="bench, squat…"
          />
        </div>
        <div className="row" style={{ gap: "0.45rem" }}>
          <button
            type="button"
            className={`chip${!category ? " active" : ""}`}
            onClick={() => {
              setPage(0);
              setCategory("");
            }}
          >
            All
          </button>
          {EXERCISE_CATEGORIES.map((c) => (
            <button
              key={c}
              type="button"
              className={`chip${category === c ? " active" : ""}`}
              onClick={() => {
                setPage(0);
                setCategory(c);
              }}
            >
              {c.replaceAll("_", " ")}
            </button>
          ))}
        </div>
      </div>

      {error ? <ErrorBanner message={error} onRetry={() => void load()} /> : null}
      {loading ? <LoadingBlock /> : null}

      {!loading && items.length === 0 ? (
        <EmptyState
          title="No exercises found"
          description="Adjust filters or create a custom exercise."
          action={
            <button type="button" className="btn btn-primary" onClick={() => setCreateOpen(true)}>
              Create exercise
            </button>
          }
        />
      ) : null}

      {!loading && items.length > 0 ? (
        <>
          <div className="card-grid">
            {items.map((exercise) => (
              <article key={exercise.id} className="card stack" style={{ gap: "0.75rem", padding: "0.85rem" }}>
                <Link to={`/exercises/${exercise.id}`} className="card-link" style={{ border: 0, transform: "none" }}>
                  <ExerciseThumbnail
                    url={exercise.thumbnailUrl}
                    name={exercise.name}
                    category={exercise.category}
                  />
                </Link>
                <div>
                  <Link
                    to={`/exercises/${exercise.id}`}
                    style={{ textDecoration: "none", color: "var(--color-text)", fontFamily: "var(--font-display)", fontWeight: 650 }}
                  >
                    {exercise.name}
                  </Link>
                  <div className="muted" style={{ fontSize: "var(--text-sm)", marginTop: "0.25rem" }}>
                    {exercise.category.replaceAll("_", " ")} · {exercise.global ? "Global" : "Custom"}
                  </div>
                </div>
                <div className="row">
                  <Link to={`/exercises/${exercise.id}`} className="btn btn-secondary" style={{ minHeight: 36, padding: "0.35rem 0.7rem" }}>
                    View
                  </Link>
                  {exercise.owned ? (
                    <>
                      <button
                        type="button"
                        className="btn btn-ghost"
                        style={{ minHeight: 36, padding: "0.35rem 0.7rem" }}
                        onClick={() => setEditing(exercise)}
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        className="btn btn-danger"
                        style={{ minHeight: 36, padding: "0.35rem 0.7rem" }}
                        disabled={busy}
                        onClick={() => void onDelete(exercise)}
                      >
                        Delete
                      </button>
                    </>
                  ) : null}
                </div>
              </article>
            ))}
          </div>
          <div className="row" style={{ justifyContent: "space-between" }}>
            <button
              type="button"
              className="btn btn-secondary"
              disabled={page <= 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              Previous
            </button>
            <span className="muted mono">
              Page {page + 1} / {Math.max(totalPages, 1)}
            </span>
            <button
              type="button"
              className="btn btn-secondary"
              disabled={page + 1 >= totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </button>
          </div>
        </>
      ) : null}

      {createOpen ? (
        <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="create-ex-title">
          <form className="modal-panel stack" onSubmit={onCreate}>
            <div className="row" style={{ justifyContent: "space-between" }}>
              <h2 id="create-ex-title">Create exercise</h2>
              <button type="button" className="btn btn-icon btn-ghost" aria-label="Close" onClick={() => setCreateOpen(false)}>
                <X size={18} />
              </button>
            </div>
            <div className="grid-2">
              <div className="field">
                <label htmlFor="new-name">Name</label>
                <input id="new-name" value={name} onChange={(e) => setName(e.target.value)} required />
              </div>
              <div className="field">
                <label htmlFor="new-category">Category</label>
                <select
                  id="new-category"
                  value={newCategory}
                  onChange={(e) => setNewCategory(e.target.value as ExerciseCategory)}
                >
                  {EXERCISE_CATEGORIES.map((c) => (
                    <option key={c} value={c}>
                      {c}
                    </option>
                  ))}
                </select>
              </div>
              <div className="field">
                <label htmlFor="new-thumb">Thumbnail URL</label>
                <input
                  id="new-thumb"
                  type="url"
                  value={media.thumbnailUrl}
                  onChange={(e) => setMedia({ ...media, thumbnailUrl: e.target.value })}
                  placeholder="https://…"
                />
              </div>
              <div className="field">
                <label htmlFor="new-demo">Demo video URL</label>
                <input
                  id="new-demo"
                  type="url"
                  value={media.demoVideoUrl}
                  onChange={(e) => setMedia({ ...media, demoVideoUrl: e.target.value })}
                  placeholder="https://youtube.com/…"
                />
              </div>
            </div>
            <div className="field">
              <label htmlFor="new-attr">
                Media attribution {media.demoVideoUrl.trim() ? "(required)" : ""}
              </label>
              <input
                id="new-attr"
                value={media.mediaAttribution}
                onChange={(e) => setMedia({ ...media, mediaAttribution: e.target.value })}
                placeholder="e.g. Demonstration via ExRx.net"
                required={!!media.demoVideoUrl.trim()}
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

      {editing ? (
        <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="edit-ex-title">
          <form className="modal-panel stack" onSubmit={onSaveEdit}>
            <div className="row" style={{ justifyContent: "space-between" }}>
              <h2 id="edit-ex-title">Edit exercise</h2>
              <button type="button" className="btn btn-icon btn-ghost" aria-label="Close" onClick={() => setEditing(null)}>
                <X size={18} />
              </button>
            </div>
            <div className="grid-2">
              <div className="field">
                <label htmlFor="edit-name">Name</label>
                <input
                  id="edit-name"
                  value={editing.name}
                  onChange={(e) => setEditing({ ...editing, name: e.target.value })}
                  required
                />
              </div>
              <div className="field">
                <label htmlFor="edit-category">Category</label>
                <select
                  id="edit-category"
                  value={editing.category}
                  onChange={(e) =>
                    setEditing({ ...editing, category: e.target.value as ExerciseCategory })
                  }
                >
                  {EXERCISE_CATEGORIES.map((c) => (
                    <option key={c} value={c}>
                      {c}
                    </option>
                  ))}
                </select>
              </div>
              <div className="field">
                <label htmlFor="edit-thumb">Thumbnail URL</label>
                <input
                  id="edit-thumb"
                  type="url"
                  value={editing.thumbnailUrl ?? ""}
                  onChange={(e) => setEditing({ ...editing, thumbnailUrl: e.target.value || null })}
                />
              </div>
              <div className="field">
                <label htmlFor="edit-demo">Demo video URL</label>
                <input
                  id="edit-demo"
                  type="url"
                  value={editing.demoVideoUrl ?? ""}
                  onChange={(e) => setEditing({ ...editing, demoVideoUrl: e.target.value || null })}
                />
              </div>
            </div>
            <div className="field">
              <label htmlFor="edit-attr">
                Media attribution {editing.demoVideoUrl?.trim() ? "(required)" : ""}
              </label>
              <input
                id="edit-attr"
                value={editing.mediaAttribution ?? ""}
                onChange={(e) =>
                  setEditing({ ...editing, mediaAttribution: e.target.value || null })
                }
                required={!!editing.demoVideoUrl?.trim()}
              />
            </div>
            <div className="row">
              <button type="submit" className="btn btn-primary" disabled={busy}>
                Save
              </button>
              <button type="button" className="btn btn-ghost" onClick={() => setEditing(null)}>
                Cancel
              </button>
            </div>
          </form>
        </div>
      ) : null}
    </div>
  );
}
