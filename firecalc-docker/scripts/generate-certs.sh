#!/usr/bin/env bash
#
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
#
# =============================================================================
# FireCalc Docker — Self-Signed Certificate Generation
# =============================================================================
# Generates self-signed SSL certificates for initial standalone deployment.
# These allow nginx to start before Let's Encrypt certificates are obtained.
# The init-letsencrypt.sh script replaces them with real certificates later.
#
# Prerequisites:
#   - .env file with UI_DOMAIN and API_DOMAIN set
#
# Usage:
#   bash scripts/generate-certs.sh
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

log() { echo "[generate-certs] $*"; }
die() { log "FATAL: $*" >&2; exit 1; }

# Load environment
if [[ -f "$PROJECT_DIR/.env" ]]; then
    set -a
    # shellcheck source=/dev/null
    source "$PROJECT_DIR/.env"
    set +a
fi

UI_DOMAIN="${UI_DOMAIN:-}"
API_DOMAIN="${API_DOMAIN:-}"

[[ -n "$UI_DOMAIN" ]] || die "UI_DOMAIN must be set in .env"
[[ -n "$API_DOMAIN" ]] || die "API_DOMAIN must be set in .env"

CERTS_DIR="$PROJECT_DIR/.certs"

command -v openssl >/dev/null 2>&1 || die "openssl is required but not found"

# ---------------------------------------------------------------------------
# Generate self-signed certificates
# ---------------------------------------------------------------------------

generate_cert() {
    local domain="$1"
    local cert_dir="$CERTS_DIR/live/$domain"

    if [[ -f "$cert_dir/fullchain.pem" && -f "$cert_dir/privkey.pem" ]]; then
        log "Certificates for '$domain' already exist, skipping"
        return 0
    fi

    mkdir -p "$cert_dir"
    log "Generating self-signed certificate for '$domain'..."

    openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
        -keyout "$cert_dir/privkey.pem" \
        -out "$cert_dir/fullchain.pem" \
        -subj "/CN=$domain/O=FireCalc Development/C=US"
    chmod 600 "$cert_dir/privkey.pem"

    log "Self-signed certificate for '$domain' → $cert_dir/"
}

log "Generating self-signed certificates for initial deployment..."
log "(Replace with Let's Encrypt certs via 'bash scripts/init-letsencrypt.sh')"
log ""

generate_cert "$UI_DOMAIN"
generate_cert "$API_DOMAIN"

log ""
log "Self-signed certificates generated."
log "WARNING: Browsers will show certificate warnings until real certs are installed."
