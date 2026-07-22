-- Batch 4: set types, RIR, per-side weight entry, set completion, exercise-level notes

ALTER TABLE workout_sets
    ADD COLUMN set_type VARCHAR(20) NOT NULL DEFAULT 'WORKING',
    ADD COLUMN rir INTEGER,
    ADD COLUMN weight_entry_mode VARCHAR(20) NOT NULL DEFAULT 'TOTAL',
    ADD COLUMN bar_weight NUMERIC(8, 2),
    ADD COLUMN entered_weight NUMERIC(8, 2),
    ADD COLUMN note VARCHAR(280),
    ADD COLUMN completed BOOLEAN NOT NULL DEFAULT TRUE;

-- Backfill entered_weight from normalized total for existing rows
UPDATE workout_sets SET entered_weight = weight WHERE entered_weight IS NULL;

ALTER TABLE workout_sets
    ALTER COLUMN entered_weight SET NOT NULL;

ALTER TABLE workout_sets
    ADD CONSTRAINT workout_sets_set_type_check CHECK (
        set_type IN ('WARMUP', 'WORKING', 'BACKOFF', 'DROP', 'FAILURE', 'AMRAP')
    ),
    ADD CONSTRAINT workout_sets_weight_entry_mode_check CHECK (
        weight_entry_mode IN ('TOTAL', 'PER_SIDE')
    ),
    ADD CONSTRAINT workout_sets_rir_check CHECK (
        rir IS NULL OR (rir >= 0 AND rir <= 5)
    ),
    ADD CONSTRAINT workout_sets_bar_weight_check CHECK (
        bar_weight IS NULL OR bar_weight >= 0
    ),
    ADD CONSTRAINT workout_sets_entered_weight_check CHECK (
        entered_weight >= 0
    );

CREATE TABLE workout_exercise_notes (
    id           UUID         PRIMARY KEY,
    workout_id   UUID         NOT NULL REFERENCES workouts (id) ON DELETE CASCADE,
    exercise_id  UUID         NOT NULL REFERENCES exercises (id) ON DELETE RESTRICT,
    note         VARCHAR(500) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT workout_exercise_notes_unique UNIQUE (workout_id, exercise_id)
);

CREATE INDEX idx_workout_exercise_notes_workout ON workout_exercise_notes (workout_id);
