#!/usr/bin/env bash
# Builds and (optionally) sends a correctly HMAC-signed check-in webhook request against a
# running GymSync API instance. Useful for manually exercising POST /api/webhooks/checkin
# without a real gym-turnstile provider. See docs/WEBHOOKS.md.
#
# Signing scheme (must match WebhookSignatureValidator):
#   signature = hex(HMAC-SHA256(secret, "<timestamp>." + rawBody))
#
# Usage:
#   WEBHOOK_SECRET=change-me-webhook-secret ./scripts/sign-checkin-webhook.sh \
#     --event-id turnstile-evt-1 --user-id <uuid> [--source gym-turnstile-mock] [--send]
#
# Required environment:
#   WEBHOOK_SECRET   Shared HMAC secret (must match `gymsync.webhook.secret` / WEBHOOK_SECRET
#                     configured on the server). Never hardcode this in the script or commit it.
#
# Options:
#   --event-id ID     External event id (required). Must be unique per delivery; redelivering
#                      the same id is the idempotency/duplicate-delivery test case.
#   --user-id UUID     User id to check in as (required).
#   --source NAME       Value for the "source" field (default: gym-turnstile-mock).
#   --occurred-at TS     ISO-8601 instant for "occurredAt" (default: omitted, server uses now).
#   --base-url URL       API base URL (default: http://localhost:8080).
#   --send              Actually POST the request with curl instead of just printing it.
set -euo pipefail

EVENT_ID=""
USER_ID=""
SOURCE="gym-turnstile-mock"
OCCURRED_AT=""
BASE_URL="${BASE_URL:-http://localhost:8080}"
SEND=0

usage() {
  grep -E '^#( |$)' "$0" | sed -E 's/^#( )?//'
  exit "${1:-0}"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --event-id) EVENT_ID="$2"; shift 2 ;;
    --user-id) USER_ID="$2"; shift 2 ;;
    --source) SOURCE="$2"; shift 2 ;;
    --occurred-at) OCCURRED_AT="$2"; shift 2 ;;
    --base-url) BASE_URL="$2"; shift 2 ;;
    --send) SEND=1; shift ;;
    -h|--help) usage 0 ;;
    *) echo "error: unknown argument: $1" >&2; usage 1 ;;
  esac
done

if [[ -z "${WEBHOOK_SECRET:-}" ]]; then
  echo "error: WEBHOOK_SECRET environment variable is required" >&2
  exit 1
fi
if [[ -z "$EVENT_ID" ]]; then
  echo "error: --event-id is required" >&2
  exit 1
fi
if [[ -z "$USER_ID" ]]; then
  echo "error: --user-id is required" >&2
  exit 1
fi
if ! command -v openssl >/dev/null 2>&1; then
  echo "error: openssl is required" >&2
  exit 1
fi

TIMESTAMP="$(date +%s)"

if [[ -n "$OCCURRED_AT" ]]; then
  BODY=$(printf '{"eventId":"%s","userId":"%s","source":"%s","occurredAt":"%s"}' \
    "$EVENT_ID" "$USER_ID" "$SOURCE" "$OCCURRED_AT")
else
  BODY=$(printf '{"eventId":"%s","userId":"%s","source":"%s"}' "$EVENT_ID" "$USER_ID" "$SOURCE")
fi

SIGNED_PAYLOAD="${TIMESTAMP}.${BODY}"
SIGNATURE=$(printf '%s' "$SIGNED_PAYLOAD" | openssl dgst -sha256 -hmac "$WEBHOOK_SECRET" | sed 's/^.* //')

echo "Timestamp:  $TIMESTAMP"
echo "Signature:  $SIGNATURE"
echo "Body:       $BODY"
echo
echo "curl -i -X POST '$BASE_URL/api/webhooks/checkin' \\"
echo "  -H 'Content-Type: application/json' \\"
echo "  -H 'X-Webhook-Timestamp: $TIMESTAMP' \\"
echo "  -H 'X-Webhook-Signature: $SIGNATURE' \\"
echo "  -d '$BODY'"

if [[ "$SEND" -eq 1 ]]; then
  echo
  echo "==> Sending request"
  curl -i -X POST "$BASE_URL/api/webhooks/checkin" \
    -H 'Content-Type: application/json' \
    -H "X-Webhook-Timestamp: $TIMESTAMP" \
    -H "X-Webhook-Signature: $SIGNATURE" \
    -d "$BODY"
fi
