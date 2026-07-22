import { useState } from "react";
import { Play } from "lucide-react";
import { extractYouTubeId, isLikelyImageUrl, youtubeEmbedUrl } from "../lib/media";

export function YouTubeEmbed({
  urlOrId,
  title = "Exercise demonstration",
  attribution,
}: {
  urlOrId: string;
  title?: string;
  attribution?: string | null;
}) {
  const [active, setActive] = useState(false);
  const embed = youtubeEmbedUrl(urlOrId);
  const id = extractYouTubeId(urlOrId);

  if (!embed || !id) {
    return (
      <div className="media-embed" style={{ display: "grid", placeItems: "center" }}>
        <a href={urlOrId} target="_blank" rel="noopener noreferrer" className="btn btn-secondary">
          Open demonstration ↗
        </a>
      </div>
    );
  }

  return (
    <div className="stack" style={{ gap: "0.5rem" }}>
      <div className="media-embed">
        {active ? (
          <iframe
            title={title}
            src={`${embed}?autoplay=1&rel=0`}
            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
            allowFullScreen
            loading="lazy"
          />
        ) : (
          <button
            type="button"
            className="btn btn-ghost"
            aria-label={`Play ${title}`}
            onClick={() => setActive(true)}
            style={{
              position: "absolute",
              inset: 0,
              width: "100%",
              height: "100%",
              borderRadius: 0,
              background: `center / cover no-repeat url(https://i.ytimg.com/vi/${id}/hqdefault.jpg)`,
            }}
          >
            <span
              style={{
                display: "inline-flex",
                alignItems: "center",
                gap: "0.5rem",
                padding: "0.65rem 1rem",
                borderRadius: "999px",
                background: "rgba(0,0,0,0.65)",
                color: "#fff",
                fontWeight: 650,
              }}
            >
              <Play size={18} /> Play demo
            </span>
          </button>
        )}
      </div>
      {attribution ? (
        <p className="muted" style={{ margin: 0, fontSize: "var(--text-sm)" }}>
          {attribution}
        </p>
      ) : (
        <p className="muted" style={{ margin: 0, fontSize: "var(--text-sm)" }}>
          Video via YouTube embed — content remains on YouTube.
        </p>
      )}
    </div>
  );
}

export function ExerciseThumbnail({
  url,
  name,
  category,
}: {
  url: string | null;
  name: string;
  category: string;
}) {
  const [broken, setBroken] = useState(false);
  if (!url || broken || !isLikelyImageUrl(url)) {
    return (
      <div
        className="media-embed"
        style={{
          aspectRatio: "4 / 3",
          display: "grid",
          placeItems: "center",
          background: "linear-gradient(145deg, rgba(46,196,255,0.18), rgba(167,139,250,0.12))",
          fontFamily: "var(--font-display)",
          fontWeight: 700,
          color: "var(--color-text)",
          padding: "1rem",
          textAlign: "center",
        }}
        aria-label={`${name} placeholder`}
      >
        <div>
          <div style={{ fontSize: "1.1rem" }}>{name}</div>
          <div className="muted" style={{ fontSize: "var(--text-sm)", marginTop: "0.35rem" }}>
            {category}
          </div>
        </div>
      </div>
    );
  }
  return (
    <div className="media-embed" style={{ aspectRatio: "4 / 3" }}>
      <img src={url} alt="" loading="lazy" onError={() => setBroken(true)} />
    </div>
  );
}
