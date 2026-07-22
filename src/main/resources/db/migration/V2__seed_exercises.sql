-- Default global exercises (created_by IS NULL)

INSERT INTO exercises (id, name, normalized_name, category, created_by, created_at, updated_at)
VALUES
    ('a1000001-0000-4000-8000-000000000001', 'Bench Press', 'bench press', 'CHEST', NULL, NOW(), NOW()),
    ('a1000001-0000-4000-8000-000000000002', 'Squat', 'squat', 'LEGS', NULL, NOW(), NOW()),
    ('a1000001-0000-4000-8000-000000000003', 'Deadlift', 'deadlift', 'BACK', NULL, NOW(), NOW()),
    ('a1000001-0000-4000-8000-000000000004', 'Overhead Press', 'overhead press', 'SHOULDERS', NULL, NOW(), NOW()),
    ('a1000001-0000-4000-8000-000000000005', 'Barbell Row', 'barbell row', 'BACK', NULL, NOW(), NOW()),
    ('a1000001-0000-4000-8000-000000000006', 'Pull-Up', 'pull-up', 'BACK', NULL, NOW(), NOW()),
    ('a1000001-0000-4000-8000-000000000007', 'Lat Pulldown', 'lat pulldown', 'BACK', NULL, NOW(), NOW()),
    ('a1000001-0000-4000-8000-000000000008', 'Leg Press', 'leg press', 'LEGS', NULL, NOW(), NOW()),
    ('a1000001-0000-4000-8000-000000000009', 'Dumbbell Curl', 'dumbbell curl', 'ARMS', NULL, NOW(), NOW()),
    ('a1000001-0000-4000-8000-000000000010', 'Triceps Pushdown', 'triceps pushdown', 'ARMS', NULL, NOW(), NOW()),
    ('a1000001-0000-4000-8000-000000000011', 'Plank', 'plank', 'CORE', NULL, NOW(), NOW()),
    ('a1000001-0000-4000-8000-000000000012', 'Running', 'running', 'CARDIO', NULL, NOW(), NOW());
