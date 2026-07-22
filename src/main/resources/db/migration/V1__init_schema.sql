-- GymSync API initial schema (Phase 1)

CREATE TABLE users (
    id              UUID         PRIMARY KEY,
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    role            VARCHAR(50)  NOT NULL DEFAULT 'USER',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT users_email_unique UNIQUE (email),
    CONSTRAINT users_role_check CHECK (role IN ('USER', 'ADMIN'))
);

CREATE INDEX idx_users_email ON users (email);

CREATE TABLE exercises (
    id               UUID         PRIMARY KEY,
    name             VARCHAR(255) NOT NULL,
    normalized_name  VARCHAR(255) NOT NULL,
    category         VARCHAR(50)  NOT NULL,
    created_by       UUID         REFERENCES users (id) ON DELETE SET NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT exercises_category_check CHECK (
        category IN ('CHEST', 'BACK', 'LEGS', 'ARMS', 'SHOULDERS', 'CORE', 'CARDIO', 'FULL_BODY', 'OTHER')
    )
);

CREATE INDEX idx_exercises_normalized_name ON exercises (normalized_name);
CREATE INDEX idx_exercises_created_by ON exercises (created_by);

CREATE TABLE workouts (
    id             UUID         PRIMARY KEY,
    user_id        UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    started_at     TIMESTAMPTZ  NOT NULL,
    ended_at       TIMESTAMPTZ,
    status         VARCHAR(50)  NOT NULL,
    source         VARCHAR(50)  NOT NULL,
    import_status  VARCHAR(50)  NOT NULL DEFAULT 'NOT_APPLICABLE',
    version        BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT workouts_status_check CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT workouts_source_check CHECK (source IN ('MANUAL', 'WEBHOOK', 'NOTES_IMPORT')),
    CONSTRAINT workouts_import_status_check CHECK (
        import_status IN ('NOT_APPLICABLE', 'PENDING_REVIEW', 'CONFIRMED', 'REJECTED')
    ),
    CONSTRAINT workouts_completed_requires_ended_at CHECK (
        status != 'COMPLETED' OR ended_at IS NOT NULL
    )
);

CREATE INDEX idx_workouts_user_started_at ON workouts (user_id, started_at DESC);
CREATE INDEX idx_workouts_user_status ON workouts (user_id, status);

CREATE UNIQUE INDEX idx_workouts_one_active_per_user ON workouts (user_id) WHERE status = 'ACTIVE';

CREATE TABLE workout_sets (
    id           UUID           PRIMARY KEY,
    workout_id   UUID           NOT NULL REFERENCES workouts (id) ON DELETE CASCADE,
    exercise_id  UUID           NOT NULL REFERENCES exercises (id) ON DELETE RESTRICT,
    weight       NUMERIC(8, 2)  NOT NULL,
    reps         INTEGER        NOT NULL,
    set_number   INTEGER        NOT NULL,
    logged_at    TIMESTAMPTZ    NOT NULL,
    version      BIGINT         NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT workout_sets_weight_check CHECK (weight >= 0),
    CONSTRAINT workout_sets_reps_check CHECK (reps > 0),
    CONSTRAINT workout_sets_set_number_check CHECK (set_number > 0),
    CONSTRAINT workout_sets_unique_set UNIQUE (workout_id, exercise_id, set_number)
);

CREATE INDEX idx_workout_sets_workout_logged_at ON workout_sets (workout_id, logged_at);

CREATE TABLE check_in_events (
    id                   UUID         PRIMARY KEY,
    external_event_id    VARCHAR(255) NOT NULL,
    user_id              UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    source               VARCHAR(255) NOT NULL,
    received_at          TIMESTAMPTZ  NOT NULL,
    triggered_workout_id UUID         REFERENCES workouts (id) ON DELETE SET NULL,
    status               VARCHAR(50)  NOT NULL,
    failure_reason       TEXT,
    CONSTRAINT check_in_events_external_event_id_unique UNIQUE (external_event_id),
    CONSTRAINT check_in_events_status_check CHECK (
        status IN ('RECEIVED', 'PROCESSED', 'DUPLICATE', 'REJECTED', 'FAILED')
    )
);

CREATE INDEX idx_check_in_events_external_event_id ON check_in_events (external_event_id);
CREATE INDEX idx_check_in_events_user_received_at ON check_in_events (user_id, received_at DESC);
