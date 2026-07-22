-- Pending human-reviewed media suggestions (YouTube embed / Wikimedia images).
-- Suggestions never auto-publish onto exercises until confirm.

CREATE TABLE exercise_media_suggestions (
    id                      UUID          PRIMARY KEY,
    exercise_id             UUID          NOT NULL REFERENCES exercises (id) ON DELETE CASCADE,
    requested_by_user_id    UUID          REFERENCES users (id) ON DELETE SET NULL,
    thumbnail_url           VARCHAR(2048),
    demo_video_url          VARCHAR(2048),
    media_attribution       VARCHAR(500)  NOT NULL,
    source                  VARCHAR(50)   NOT NULL,
    confidence              VARCHAR(50)   NOT NULL,
    status                  VARCHAR(50)   NOT NULL,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    reviewed_at             TIMESTAMPTZ,
    CONSTRAINT exercise_media_suggestions_source_check CHECK (
        source IN ('CURATED', 'WIKIMEDIA', 'YOUTUBE_API')
    ),
    CONSTRAINT exercise_media_suggestions_confidence_check CHECK (
        confidence IN ('HIGH', 'MEDIUM', 'LOW')
    ),
    CONSTRAINT exercise_media_suggestions_status_check CHECK (
        status IN ('PENDING', 'CONFIRMED', 'REJECTED')
    )
);

CREATE INDEX idx_exercise_media_suggestions_exercise_status
    ON exercise_media_suggestions (exercise_id, status);
