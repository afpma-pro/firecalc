#!/usr/bin/env bash
#
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
#
# =============================================================================
# FireCalc Docker — Environment Setup Script
# =============================================================================
# Sets up the deployment environment:
#   1. Creates .env from .env.defaults if it doesn't exist
#   2. Creates required directories (configs/, databases/)
#   3. Validates required environment variables for the active mode
#
# The script is idempotent: safe to run multiple times.
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

ENV_FILE="$PROJECT_DIR/.env"
ENV_DEFAULTS="$PROJECT_DIR/.env.defaults"

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

log() { echo "[setup-env] $*"; }
die() { log "FATAL: $*" >&2; exit 1; }

# ---------------------------------------------------------------------------
# Create .env from defaults if missing
# ---------------------------------------------------------------------------

if [[ ! -f "$ENV_FILE" ]]; then
    if [[ -f "$ENV_DEFAULTS" ]]; then
        cp "$ENV_DEFAULTS" "$ENV_FILE"
        log "Created .env from .env.defaults"
        log "Review and update .env with your actual values before deploying."
    else
        die ".env.defaults not found at $ENV_DEFAULTS"
    fi
else
    log ".env already exists, skipping creation"
fi

# ---------------------------------------------------------------------------
# Create required directories
# ---------------------------------------------------------------------------

for dir in configs databases; do
    if [[ ! -d "$PROJECT_DIR/$dir" ]]; then
        mkdir -p "$PROJECT_DIR/$dir"
        log "Created directory: $dir/"
    fi
done

# ---------------------------------------------------------------------------
# Load environment variables
# ---------------------------------------------------------------------------

# Save CALC_SERVER before sourcing .env so a live environment setting
# isn't clobbered by a stale .env entry.
_calc_server_saved="${CALC_SERVER:-}"
_calc_server_was_set=false
if [[ -n "$_calc_server_saved" ]]; then
    _calc_server_was_set=true
fi

# Export variables from .env so they're available to subsequent commands
# (Makefile recipes and docker compose will also read .env directly)
set -a
# shellcheck source=/dev/null
source "$ENV_FILE"
set +a

# Restore CALC_SERVER if it was set before we sourced .env
if [[ "$_calc_server_was_set" = true ]]; then
    CALC_SERVER="$_calc_server_saved"
    export CALC_SERVER
fi
unset _calc_server_saved _calc_server_was_set

# ---------------------------------------------------------------------------
# Validate required variables based on deployment mode
# ---------------------------------------------------------------------------

FIRECALC_ENV="${FIRECALC_ENV:-}"
MODE="${MODE:-standalone}"
CALC_SERVER="${CALC_SERVER:-}"

# FIRECALC_ENV is always required
if [[ -z "$FIRECALC_ENV" ]]; then
    die "FIRECALC_ENV is not set in .env"
fi

log "FIRECALC_ENV=$FIRECALC_ENV"

# Standalone mode requires domain configuration
if [[ "$MODE" == "standalone" ]]; then
    UI_DOMAIN="${UI_DOMAIN:-}"
    API_DOMAIN="${API_DOMAIN:-}"

    if [[ -z "$UI_DOMAIN" ]]; then
        die "UI_DOMAIN is required for standalone mode"
    fi
    if [[ -z "$API_DOMAIN" ]]; then
        die "API_DOMAIN is required for standalone mode"
    fi

    log "Standalone mode: UI=$UI_DOMAIN, API=$API_DOMAIN"
fi

# ---------------------------------------------------------------------------
# Report active configuration
# ---------------------------------------------------------------------------

calc_label="no-calc"
if [[ "${CALC_SERVER}" = "true" ]]; then
    calc_label="with-calc"
fi

log "Active configuration:"
log "  Mode       : $MODE"
log "  Calc       : $calc_label"
log "  Environment: $FIRECALC_ENV"
log "Environment setup complete."
