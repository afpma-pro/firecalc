#!/usr/bin/env bash
#
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
#
# =============================================================================
# FireCalc Docker — Let's Encrypt Certificate Initialization
# =============================================================================
# Initializes Let's Encrypt certificates for standalone mode deployments.
# Uses certbot in webroot mode with the nginx container serving challenge
# files.
#
# This script should be run after the initial deployment when the nginx
# container is serving HTTP on port 80.
#
# Prerequisites:
#   - Standalone compose variant active (docker-compose.yml symlinked)
#   - Containers running (docker compose up -d)
#   - UI_DOMAIN and API_DOMAIN resolve to the server's public IP
#   - Ports 80 and 443 are accessible from the internet
#
# Usage:
#   bash scripts/init-letsencrypt.sh              # Production certificates
#   bash scripts/init-letsencrypt.sh --staging    # Staging certificates (no rate limits)
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

STAGING=false
# ---------------------------------------------------------------------------
# Helpers (defined early for use in argument parsing)
# ---------------------------------------------------------------------------

log() { echo "[letsencrypt] $*"; }
die() { log "FATAL: $*" >&2; exit 1; }
for arg in "$@"; do
    case "$arg" in
        --staging) STAGING=true ;;
        --help)
            echo "Usage: $0 [--staging]"
            echo ""
            echo "  --staging    Use Let's Encrypt staging CA (no rate limits)"
            exit 0
            ;;
        *) die "Unknown option: $arg" ;;
    esac
done

# Load environment
if [[ -f "$PROJECT_DIR/.env" ]]; then
    set -a
    # shellcheck source=/dev/null
    source "$PROJECT_DIR/.env"
    set +a
fi

UI_DOMAIN="${UI_DOMAIN:-}"
API_DOMAIN="${API_DOMAIN:-}"
LETSENCRYPT_EMAIL="${LETSENCRYPT_EMAIL:-}"
LETSENCRYPT_STAGING="${LETSENCRYPT_STAGING:-false}"

# Override with CLI flag if specified
if [[ "$STAGING" == "true" ]]; then
    LETSENCRYPT_STAGING="true"
fi

# ---------------------------------------------------------------------------
# Validation
# ---------------------------------------------------------------------------

[[ -n "$UI_DOMAIN" ]] || die "UI_DOMAIN must be set"
[[ -n "$API_DOMAIN" ]] || die "API_DOMAIN must be set"
[[ -n "$LETSENCRYPT_EMAIL" ]] || die "LETSENCRYPT_EMAIL must be set"

command -v docker >/dev/null 2>&1 || die "docker is required but not found"

# ---------------------------------------------------------------------------
# Determine certbot flags
# ---------------------------------------------------------------------------

STAGING_FLAG=""
if [[ "$LETSENCRYPT_STAGING" == "true" ]]; then
    STAGING_FLAG="--staging"
    log "Using Let's Encrypt STAGING CA (no rate limits)"
else
    log "Using Let's Encrypt PRODUCTION CA"
fi

# ---------------------------------------------------------------------------
# Check if containers are running
# ---------------------------------------------------------------------------

COMPOSE_FILE="$PROJECT_DIR/docker-compose.yml"
if [[ ! -L "$COMPOSE_FILE" ]]; then
    die "No active compose variant. Run 'make deploy' or 'make build' first."
fi

log "Checking container status..."
cd "$PROJECT_DIR"

# Check if nginx container is running
if ! docker compose ps nginx --status running >/dev/null 2>&1; then
    die "nginx container is not running. Start with 'make up' first."
fi

# ---------------------------------------------------------------------------
# Request certificates
# ---------------------------------------------------------------------------

DOMAINS=("$UI_DOMAIN" "$API_DOMAIN")
log "Requesting certificates for: ${DOMAINS[*]}"
log "Email: $LETSENCRYPT_EMAIL"

# Build -d flags for certbot (one -d per domain)
CERTBOT_DOMAIN_ARGS=()
for domain in "${DOMAINS[@]}"; do
    CERTBOT_DOMAIN_ARGS+=(-d "$domain")
done

# Run certbot in a one-off container
# The certs and webroot directories are shared with the running services
docker run --rm \
    -v "$PROJECT_DIR/.certs:/etc/letsencrypt" \
    -v "$PROJECT_DIR/certbot-webroot:/var/www/certbot" \
    certbot/certbot:v2.11.0 certonly \
    --webroot \
    --webroot-path /var/www/certbot \
    --email "$LETSENCRYPT_EMAIL" \
    --agree-tos \
    --non-interactive \
    ${STAGING_FLAG:+$STAGING_FLAG} \
    "${CERTBOT_DOMAIN_ARGS[@]}"

log "Certificates obtained successfully."
log ""
log "Certificate locations:"
log "  Certs: $PROJECT_DIR/.certs/live/"
log ""
log "Restart nginx to pick up the new certificates:"
log "  docker compose restart nginx"
log ""
log "Certificate renewal is handled automatically by the certbot service"
log "in the compose stack (runs every 12 hours)."
