import { describe, expect, it } from "vitest";
import { extractYouTubeId, youtubeEmbedUrl, isLikelyImageUrl } from "./media";

describe("media helpers", () => {
  it("extracts YouTube IDs from watch URLs and bare IDs", () => {
    expect(extractYouTubeId("dQw4w9WgXcQ")).toBe("dQw4w9WgXcQ");
    expect(extractYouTubeId("https://www.youtube.com/watch?v=dQw4w9WgXcQ")).toBe("dQw4w9WgXcQ");
    expect(extractYouTubeId("https://youtu.be/dQw4w9WgXcQ")).toBe("dQw4w9WgXcQ");
  });

  it("builds embed URLs", () => {
    expect(youtubeEmbedUrl("dQw4w9WgXcQ")).toBe("https://www.youtube.com/embed/dQw4w9WgXcQ");
  });

  it("detects image URLs", () => {
    expect(isLikelyImageUrl("https://upload.wikimedia.org/wikipedia/commons/a/a1/foo.jpg")).toBe(
      true,
    );
    expect(isLikelyImageUrl("https://example.com/page")).toBe(false);
  });
});
