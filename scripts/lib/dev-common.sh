# Shared helpers for scripts/start-local.sh and scripts/stop-local.sh.
# Not meant to be executed directly -- source it after `cd` to the repo root.

API_PORT="${API_PORT:-8080}"
DB_PORT="${DB_PORT:-5432}"
FRONTEND_PORTS=(5173 5174)

log() { echo "$@"; }
err() { echo "error: $*" >&2; }

# Print the PIDs (space-separated) currently LISTENing on $1, or nothing.
pids_on_port() {
  lsof -nP -ti "tcp:$1" -sTCP:LISTEN 2>/dev/null || true
}

# Kill whatever is listening on port $1. Docker-managed listeners (the
# Docker Desktop backend proxying a container's published port) are never
# killed directly -- they belong to `docker compose down`, which must run
# first. Anything else found is a stray process from a previous session and
# is reported and killed.
free_port() {
  local port="$1" pids pid comm
  pids="$(pids_on_port "$port")"
  [[ -z "$pids" ]] && return 0

  for pid in $pids; do
    comm="$(ps -p "$pid" -o comm= 2>/dev/null || echo unknown)"
    if [[ "$comm" == *docker* || "$comm" == *vpnkit* ]]; then
      log "  port $port is still held by Docker ($comm, pid $pid) -- run 'docker compose down' first"
      continue
    fi
    log "  killing stray process on port $port: pid $pid ($comm)"
    kill "$pid" 2>/dev/null || true
  done

  sleep 1
  pids="$(pids_on_port "$port")"
  for pid in $pids; do
    comm="$(ps -p "$pid" -o comm= 2>/dev/null || echo unknown)"
    [[ "$comm" == *docker* || "$comm" == *vpnkit* ]] && continue
    log "  process $pid on port $port did not stop, sending SIGKILL"
    kill -9 "$pid" 2>/dev/null || true
  done
}

# Stop any docker-compose-managed services for this project (handles the
# postgres container, and the optional full-stack `app` container).
stop_compose_services() {
  command -v docker >/dev/null 2>&1 || return 0
  if docker compose ps -q 2>/dev/null | grep -q .; then
    log "  stopping docker compose services"
    docker compose down
  fi
}

# Free ports 8080, 5432, 5173/5174, preferring `docker compose down` for
# anything container-managed and killing stray host processes otherwise.
cleanup_ports() {
  log "==> Cleaning up ports ${API_PORT}, ${DB_PORT}, ${FRONTEND_PORTS[*]}"
  stop_compose_services
  free_port "$API_PORT"
  free_port "$DB_PORT"
  for p in "${FRONTEND_PORTS[@]}"; do
    free_port "$p"
  done
}
