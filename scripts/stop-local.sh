#!/usr/bin/env bash
# Tear down everything scripts/start-local.sh started: the frontend dev
# server, the Spring Boot API, and the Postgres container. No manual PID
# hunting required -- ports are freed the same way start-local.sh frees them
# before starting, so this is safe to run even if nothing is up.
#
# Usage:
#   ./scripts/stop-local.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
# shellcheck source=lib/dev-common.sh
source "$ROOT/scripts/lib/dev-common.sh"

log "==> Stopping GymSync local dev stack"
cleanup_ports
rm -f logs/api.pid logs/frontend.pid

log "Done. Ports ${API_PORT}, ${DB_PORT}, ${FRONTEND_PORTS[*]} are free."
