-- Exercise media: external links only (no object storage / uploaded files)

ALTER TABLE exercises
    ADD COLUMN thumbnail_url      VARCHAR(2048),
    ADD COLUMN demo_video_url     VARCHAR(2048),
    ADD COLUMN media_attribution  VARCHAR(500);

-- Seeded globals intentionally keep thumbnail_url NULL: only populate when a
-- permissively-licensed source URL is confirmed. Do not fabricate image links.
