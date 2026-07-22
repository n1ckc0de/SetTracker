-- Readiness check-ins and AI coach recommendations (Batch 1)

CREATE TABLE readiness_check_ins (
    id              UUID           PRIMARY KEY,
    user_id         UUID           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    workout_id      UUID           REFERENCES workouts (id) ON DELETE SET NULL,
    sleep_hours     NUMERIC(4, 2),
    sleep_quality   INTEGER,
    energy_level    INTEGER,
    soreness        INTEGER,
    stress          INTEGER,
    note            VARCHAR(500),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT readiness_sleep_hours_check CHECK (sleep_hours IS NULL OR (sleep_hours >= 0 AND sleep_hours <= 24)),
    CONSTRAINT readiness_sleep_quality_check CHECK (sleep_quality IS NULL OR (sleep_quality BETWEEN 1 AND 5)),
    CONSTRAINT readiness_energy_check CHECK (energy_level IS NULL OR (energy_level BETWEEN 1 AND 5)),
    CONSTRAINT readiness_soreness_check CHECK (soreness IS NULL OR (soreness BETWEEN 1 AND 5)),
    CONSTRAINT readiness_stress_check CHECK (stress IS NULL OR (stress BETWEEN 1 AND 5))
);

CREATE INDEX idx_readiness_user_created_at ON readiness_check_ins (user_id, created_at DESC);
CREATE INDEX idx_readiness_user_workout ON readiness_check_ins (user_id, workout_id);

CREATE TABLE coach_recommendations (
    id                      UUID          PRIMARY KEY,
    user_id                 UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    exercise_id             UUID          REFERENCES exercises (id) ON DELETE SET NULL,
    based_on_workout_id     UUID          REFERENCES workouts (id) ON DELETE SET NULL,
    readiness_check_in_id   UUID          REFERENCES readiness_check_ins (id) ON DELETE SET NULL,
    recommendation_type     VARCHAR(50)   NOT NULL,
    previous_performance    TEXT          NOT NULL,
    proposed_change         TEXT          NOT NULL,
    reasoning               TEXT          NOT NULL,
    confidence_level        VARCHAR(20)   NOT NULL,
    status                  VARCHAR(20)   NOT NULL,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    responded_at            TIMESTAMPTZ,
    version                 BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT coach_rec_type_check CHECK (
        recommendation_type IN ('WEIGHT_ADJUSTMENT', 'VOLUME_ADJUSTMENT', 'DELOAD', 'GENERAL', 'SAFETY_HOLD')
    ),
    CONSTRAINT coach_rec_confidence_check CHECK (confidence_level IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT coach_rec_status_check CHECK (
        status IN ('PROPOSED', 'ACCEPTED', 'MODIFIED', 'DISMISSED')
    )
);

CREATE INDEX idx_coach_rec_user_status ON coach_recommendations (user_id, status);
CREATE INDEX idx_coach_rec_user_created ON coach_recommendations (user_id, created_at DESC);
CREATE INDEX idx_coach_rec_exercise ON coach_recommendations (exercise_id);
