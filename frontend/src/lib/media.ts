/** Helpers for exercise demo media — YouTube embeds and plain image URLs. */

const YT_HOSTS = new Set([
  "youtube.com",
  "www.youtube.com",
  "m.youtube.com",
  "youtu.be",
  "www.youtu.be",
]);

export function extractYouTubeId(urlOrId: string | null | undefined): string | null {
  if (!urlOrId) {
    return null;
  }
  const raw = urlOrId.trim();
  if (/^[\w-]{11}$/.test(raw)) {
    return raw;
  }
  try {
    const u = new URL(raw);
    if (!YT_HOSTS.has(u.hostname)) {
      return null;
    }
    if (u.hostname.includes("youtu.be")) {
      const id = u.pathname.split("/").filter(Boolean)[0];
      return id && /^[\w-]{11}$/.test(id) ? id : null;
    }
    const v = u.searchParams.get("v");
    if (v && /^[\w-]{11}$/.test(v)) {
      return v;
    }
    const embed = u.pathname.match(/\/embed\/([\w-]{11})/);
    return embed?.[1] ?? null;
  } catch {
    return null;
  }
}

export function youtubeEmbedUrl(urlOrId: string): string | null {
  const id = extractYouTubeId(urlOrId);
  return id ? `https://www.youtube.com/embed/${id}` : null;
}

export function isLikelyImageUrl(url: string | null | undefined): boolean {
  if (!url) {
    return false;
  }
  return /\.(png|jpe?g|webp|gif|svg)(\?|$)/i.test(url) || url.includes("wikimedia.org");
}
