#!/usr/bin/env bash
# Minimal smoke test against a running GymSync API (default http://localhost:8080).
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
EMAIL="smoke-$(date +%s)@example.com"
PASSWORD="SmokeTestPass1!"
NAME="Smoke Tester"

echo "==> Health"
curl -sf "$BASE_URL/actuator/health" | grep -q UP

echo "==> Register $EMAIL"
REGISTER=$(curl -sf -X POST "$BASE_URL/api/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"name\":\"$NAME\"}")

TOKEN=$(echo "$REGISTER" | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")
AUTH="Authorization: Bearer $TOKEN"

echo "==> List exercises"
curl -sf -H "$AUTH" "$BASE_URL/api/exercises?size=5" >/dev/null

echo "==> Start workout"
WORKOUT=$(curl -sf -X POST -H "$AUTH" -H 'Content-Type: application/json' \
  "$BASE_URL/api/workouts" -d '{}')
WORKOUT_ID=$(echo "$WORKOUT" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

EXERCISE_ID=$(curl -sf -H "$AUTH" "$BASE_URL/api/exercises?query=bench&size=1" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['content'][0]['id'] if 'content' in d else d['items'][0]['id'])")

echo "==> Log set"
curl -sf -X POST -H "$AUTH" -H 'Content-Type: application/json' \
  "$BASE_URL/api/workouts/$WORKOUT_ID/sets" \
  -d "{\"exerciseId\":\"$EXERCISE_ID\",\"weight\":135,\"reps\":8,\"setNumber\":1}" >/dev/null

echo "==> Complete workout"
curl -sf -X PATCH -H "$AUTH" "$BASE_URL/api/workouts/$WORKOUT_ID/complete" >/dev/null

echo "==> Weekly stats"
curl -sf -H "$AUTH" "$BASE_URL/api/stats/weekly" >/dev/null

echo "Smoke test passed."
