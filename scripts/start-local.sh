#!/usr/bin/env bash
# Bring up the full local dev stack (Postgres, Spring Boot API, Vite frontend)
# in the right order, from a clean state, every time.
#
# Safe to re-run: it tears down anything already running on this project's
# ports before starting fresh, so a stuck previous session never causes a
# port conflict or duplicate process.
#
# Usage:
#   ./scripts/start-local.sh
#
# If a .env file exists at the repo root, it's sourced and exported before the
# API starts, so DB/JWT/webhook secrets and LLM_PROVIDER/LLM_API_KEY/LLM_API_URL/
# LLM_MODEL reach `./mvnw spring-boot:run` automatically. Safe to omit.
#
# Env overrides:
#   API_PORT (default 8080), DB_PORT (default 5432)
#   PG_READY_TIMEOUT   seconds to wait for Postgres to accept connections (default 60)
#   API_HEALTH_TIMEOUT seconds to wait for GET /actuator/health to report UP (default 90)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
# shellcheck source=lib/dev-common.sh
source "$ROOT/scripts/lib/dev-common.sh"

PG_READY_TIMEOUT="${PG_READY_TIMEOUT:-60}"
API_HEALTH_TIMEOUT="${API_HEALTH_TIMEOUT:-90}"

for bin in docker curl lsof; do
  if ! command -v "$bin" >/dev/null 2>&1; then
    err "$bin is required but not found on PATH"
    exit 1
  fi
done

if [[ -z "${JAVA_HOME:-}" ]] && command -v /usr/libexec/java_home >/dev/null 2>&1; then
  JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
  export JAVA_HOME
fi

# --- 0. Load .env ------------------------------------------------------------
# `./mvnw spring-boot:run` is a plain child process -- it only sees what's
# already in this shell's environment, so .env (DB/JWT/webhook secrets, and
# LLM_PROVIDER/LLM_API_KEY/LLM_API_URL/LLM_MODEL) has to be sourced and
# exported here, not just left on disk.
if [[ -f "$ROOT/.env" ]]; then
  log "==> Loading environment from .env"
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
else
  log "==> No .env found, skipping (cp .env.example .env to set secrets/LLM config)"
fi

mkdir -p logs

# --- 1. Clean slate ---------------------------------------------------------
cleanup_ports

# --- 2. Postgres -------------------------------------------------------------
log "==> Starting PostgreSQL (docker compose up -d postgres)"
docker compose up -d postgres

log "==> Waiting for PostgreSQL to accept connections (timeout ${PG_READY_TIMEOUT}s)"
elapsed=0
until docker compose exec -T postgres pg_isready -U gymsync -d gymsync >/dev/null 2>&1; do
  if (( elapsed >= PG_READY_TIMEOUT )); then
    err "PostgreSQL did not become ready within ${PG_READY_TIMEOUT}s"
    err "check: docker compose logs postgres"
    exit 1
  fi
  printf "."
  sleep 2
  elapsed=$((elapsed + 2))
done
echo
log "PostgreSQL is ready."

# --- 3. API ------------------------------------------------------------------
log "==> Starting Spring Boot API in the background (logs/api.log)"
nohup ./mvnw spring-boot:run -Dspring-boot.run.profiles=local \
  > logs/api.log 2>&1 &
echo $! > logs/api.pid

log "==> Waiting for API health check at http://localhost:${API_PORT}/actuator/health (timeout ${API_HEALTH_TIMEOUT}s)"
elapsed=0
api_up=0
while (( elapsed < API_HEALTH_TIMEOUT )); do
  health="$(curl -fsS "http://localhost:${API_PORT}/actuator/health" 2>/dev/null || true)"
  if [[ "$health" == *'"status":"UP"'* ]]; then
    api_up=1
    break
  fi
  printf "."
  sleep 2
  elapsed=$((elapsed + 2))
done
echo

if [[ "$api_up" -ne 1 ]]; then
  err "API did not report UP within ${API_HEALTH_TIMEOUT}s"
  err "check: tail -f logs/api.log"
  exit 1
fi
log "API is UP."

# --- 4. Frontend ---------------------------------------------------------------
log "==> Starting frontend in the background (logs/frontend.log)"
if [[ ! -d "$ROOT/frontend/node_modules" ]]; then
  log "  frontend/node_modules missing, running npm install first"
  (cd "$ROOT/frontend" && npm install)
fi
(cd "$ROOT/frontend" && exec npm run dev) > logs/frontend.log 2>&1 &
echo $! > logs/frontend.pid

# Frontend takes a moment to bind its port; give it a short grace window so
# the summary below can report an accurate URL.
frontend_port=5173
for _ in 1 2 3 4 5 6 7 8 9 10; do
  if [[ -n "$(pids_on_port 5173)" ]]; then
    frontend_port=5173
    break
  fi
  if [[ -n "$(pids_on_port 5174)" ]]; then
    frontend_port=5174
    break
  fi
  sleep 1
done

# --- 5. Summary ----------------------------------------------------------------
echo
echo "================================================================"
echo " GymSync local dev stack is up"
echo "================================================================"
echo " API health:   UP"
echo " API URL:      http://localhost:${API_PORT}"
echo " Swagger UI:   http://localhost:${API_PORT}/swagger-ui.html"
echo " Frontend URL: http://localhost:${frontend_port}"
echo
echo " Logs:"
echo "   API      -> logs/api.log       (tail -f logs/api.log)"
echo "   Frontend -> logs/frontend.log  (tail -f logs/frontend.log)"
echo "   Postgres -> docker compose logs -f postgres"
echo
echo " Stop everything: ./scripts/stop-local.sh"
echo "================================================================"
