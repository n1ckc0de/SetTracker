-- Program builder: user-owned training plans (planning data — hard-delete cascades)

CREATE TABLE programs (
    id           UUID          PRIMARY KEY,
    user_id      UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name         VARCHAR(255)  NOT NULL,
    description  VARCHAR(2000),
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version      BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX idx_programs_user_updated_at ON programs (user_id, updated_at DESC);

CREATE TABLE program_days (
    id           UUID          PRIMARY KEY,
    program_id   UUID          NOT NULL REFERENCES programs (id) ON DELETE CASCADE,
    day_number   INTEGER       NOT NULL,
    name         VARCHAR(255)  NOT NULL,
    CONSTRAINT program_days_day_number_check CHECK (day_number > 0),
    CONSTRAINT program_days_unique_day UNIQUE (program_id, day_number)
);

CREATE INDEX idx_program_days_program ON program_days (program_id, day_number);

CREATE TABLE program_day_exercises (
    id               UUID           PRIMARY KEY,
    program_day_id   UUID           NOT NULL REFERENCES program_days (id) ON DELETE CASCADE,
    exercise_id      UUID           NOT NULL REFERENCES exercises (id) ON DELETE RESTRICT,
    order_in_day     INTEGER        NOT NULL,
    target_sets      INTEGER        NOT NULL,
    target_reps_min  INTEGER,
    target_reps_max  INTEGER,
    target_weight    NUMERIC(8, 2),
    CONSTRAINT program_day_exercises_order_check CHECK (order_in_day > 0),
    CONSTRAINT program_day_exercises_sets_check CHECK (target_sets > 0),
    CONSTRAINT program_day_exercises_reps_min_check CHECK (target_reps_min IS NULL OR target_reps_min > 0),
    CONSTRAINT program_day_exercises_reps_max_check CHECK (target_reps_max IS NULL OR target_reps_max > 0),
    CONSTRAINT program_day_exercises_weight_check CHECK (target_weight IS NULL OR target_weight >= 0),
    CONSTRAINT program_day_exercises_unique_order UNIQUE (program_day_id, order_in_day)
);

CREATE INDEX idx_program_day_exercises_day ON program_day_exercises (program_day_id, order_in_day);
CREATE INDEX idx_program_day_exercises_exercise ON program_day_exercises (exercise_id);
