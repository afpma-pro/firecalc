#!/usr/bin/env bash
#
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
#
# =============================================================================
# FireCalc Docker — Generic Container Entrypoint
# =============================================================================
# Generic container entrypoint for FireCalc services. Validates the environment,
# runs initialization hooks, starts the main process, and handles graceful
# shutdown on SIGTERM/SIGINT.
#
# Environment Variables:
#   FIRECALC_ENV     — Environment name (required)
#   ENTRYPOINT_CMD   — Command to exec as the main process (required)
#   ENTRYPOINT_HOOKS — Space-separated list of init hook scripts to run
#                      before starting the main process (optional)
#
# Usage:
#   ENTRYPOINT_CMD="java -jar /app/app.jar" bash scripts/entrypoint.sh
#   ENTRYPOINT_CMD="nginx -g 'daemon off;'" bash scripts/entrypoint.sh
# =============================================================================

set -euo pipefail

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

FIRECALC_ENV="${FIRECALC_ENV:-}"
ENTRYPOINT_CMD="${ENTRYPOINT_CMD:-}"
ENTRYPOINT_HOOKS="${ENTRYPOINT_HOOKS:-}"
CONTAINER_NAME="${FIRECALC_ENV:-firecalc}-container"

# ---------------------------------------------------------------------------
# Validation
# ---------------------------------------------------------------------------

if [[ -z "$FIRECALC_ENV" ]]; then
    echo "[entrypoint] ERROR: FIRECALC_ENV must be set" >&2
    exit 1
fi

if [[ -z "$ENTRYPOINT_CMD" ]]; then
    echo "[entrypoint] ERROR: ENTRYPOINT_CMD must be set" >&2
    exit 1
fi

echo "[entrypoint] Starting $CONTAINER_NAME (env: $FIRECALC_ENV)"

# ---------------------------------------------------------------------------
# Signal handling
# ---------------------------------------------------------------------------

MAIN_PID=""

cleanup() {
    local exit_code=$?
    if [[ -n "$MAIN_PID" ]] && kill -0 "$MAIN_PID" 2>/dev/null; then
        echo "[entrypoint] Received shutdown signal, stopping main process (PID $MAIN_PID)..."
        kill -TERM "$MAIN_PID" 2>/dev/null || true
        wait "$MAIN_PID" 2>/dev/null || true
    fi
    echo "[entrypoint] Shutdown complete (exit code: $exit_code)"
    exit "$exit_code"
}

trap cleanup SIGTERM SIGINT

# ---------------------------------------------------------------------------
# Run initialization hooks
# ---------------------------------------------------------------------------

if [[ -n "$ENTRYPOINT_HOOKS" ]]; then
    for hook in $ENTRYPOINT_HOOKS; do
        if [[ -x "$hook" ]]; then
            echo "[entrypoint] Running init hook: $hook"
            "$hook" || { echo "[entrypoint] Hook $hook failed" >&2; exit 1; }
        else
            echo "[entrypoint] WARNING: Init hook not found or not executable: $hook" >&2
        fi
    done
fi

# ---------------------------------------------------------------------------
# Start main process
# ---------------------------------------------------------------------------

echo "[entrypoint] Exec: $ENTRYPOINT_CMD"
eval "$ENTRYPOINT_CMD" &
MAIN_PID=$!

wait "$MAIN_PID"
exit_code=$?
MAIN_PID=""
exit "$exit_code"
