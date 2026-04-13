#!/usr/bin/env bash
#
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
#

# =============================================================================
# FireCalc — Initial Let's Encrypt Certificate Provisioning
# =============================================================================
# Solves the chicken-and-egg problem: nginx needs TLS certificates to start,
# but certbot needs nginx running to complete the ACME HTTP-01 challenge.
#
# This script:
#   1. Generates self-signed bootstrap certificates
#   2. Starts nginx (HTTPS works with the self-signed cert)
#   3. Obtains real Let's Encrypt certificates via certbot --webroot
#   4. Reloads nginx with the real certificates
#
# Usage:
#   cd docker
#   chmod +x init-letsencrypt.sh
#   ./init-letsencrypt.sh
#
# Set LETSENCRYPT_STAGING=true in .env to use the Let's Encrypt staging
# environment (avoids rate limits during testing).
# =============================================================================

set -euo pipefail

# ---------------------------------------------------------------------------
# Load environment
# ---------------------------------------------------------------------------
if [ ! -f .env ]; then
    echo "ERROR: .env file not found. Copy .env.example to .env and configure it."
    exit 1
fi

# shellcheck source=/dev/null
source .env

# ---------------------------------------------------------------------------
# Validate required variables
# ---------------------------------------------------------------------------
if [ -z "${UI_DOMAIN:-}" ]; then
    echo "ERROR: UI_DOMAIN is not set in .env"
    exit 1
fi
if [ -z "${API_DOMAIN:-}" ]; then
    echo "ERROR: API_DOMAIN is not set in .env"
    exit 1
fi

LETSENCRYPT_EMAIL="${LETSENCRYPT_EMAIL:-}"
LETSENCRYPT_STAGING="${LETSENCRYPT_STAGING:-false}"

echo "========================================"
echo " FireCalc — Certificate Provisioning"
echo "========================================"
echo "  UI domain : ${UI_DOMAIN}"
echo "  API domain: ${API_DOMAIN}"
echo "  Email     : ${LETSENCRYPT_EMAIL:-<not set>}"
echo "  Staging   : ${LETSENCRYPT_STAGING}"
echo "========================================"
echo ""

# ---------------------------------------------------------------------------
# Step 1 — Generate self-signed bootstrap certificates
# ---------------------------------------------------------------------------
CERT_DIR="./data/certbot/conf/live/${UI_DOMAIN}"

echo "[1/4] Generating self-signed bootstrap certificates ..."

docker compose run --rm --entrypoint "" certbot sh -c "
    mkdir -p /etc/letsencrypt/live/${UI_DOMAIN} && \
    openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
        -keyout /etc/letsencrypt/live/${UI_DOMAIN}/privkey.pem \
        -out    /etc/letsencrypt/live/${UI_DOMAIN}/fullchain.pem \
        -subj   '/CN=localhost'
"

echo "  -> Self-signed certificates created."
echo ""

# ---------------------------------------------------------------------------
# Step 2 — Start nginx with self-signed certs
# ---------------------------------------------------------------------------
echo "[2/4] Starting nginx with bootstrap certificates ..."
docker compose up -d nginx
echo "  -> nginx is running."
echo ""

# Give nginx a moment to bind ports
sleep 2

# ---------------------------------------------------------------------------
# Step 3 — Request real certificates from Let's Encrypt
# ---------------------------------------------------------------------------
echo "[3/4] Requesting real certificates from Let's Encrypt ..."

STAGING_FLAG=""
if [ "${LETSENCRYPT_STAGING}" = "true" ]; then
    STAGING_FLAG="--staging"
    echo "  (using Let's Encrypt STAGING environment)"
fi

EMAIL_FLAG="--register-unsafely-without-email"
if [ -n "${LETSENCRYPT_EMAIL}" ]; then
    EMAIL_FLAG="--email ${LETSENCRYPT_EMAIL}"
fi

# Remove the self-signed bootstrap certs so certbot can write real ones
docker compose run --rm --entrypoint "" certbot sh -c "
    rm -rf /etc/letsencrypt/live/${UI_DOMAIN} && \
    rm -rf /etc/letsencrypt/archive/${UI_DOMAIN} && \
    rm -rf /etc/letsencrypt/renewal/${UI_DOMAIN}.conf
"

docker compose run --rm --entrypoint certbot certbot certonly \
    --webroot \
    -w /var/www/certbot \
    -d "${UI_DOMAIN}" \
    -d "${API_DOMAIN}" \
    ${EMAIL_FLAG} \
    ${STAGING_FLAG} \
    --agree-tos \
    --no-eff-email \
    --force-renewal

echo "  -> Real certificates obtained."
echo ""

# ---------------------------------------------------------------------------
# Step 4 — Reload nginx with real certificates
# ---------------------------------------------------------------------------
echo "[4/4] Reloading nginx with real certificates ..."
docker compose exec nginx nginx -s reload
echo "  -> nginx reloaded."
echo ""

echo "========================================"
echo " Certificate provisioning complete!"
echo "========================================"
echo ""
echo "You can now start the full stack:"
echo "  docker compose up -d"
echo ""
echo "Verify certificates:"
echo "  curl -I https://${UI_DOMAIN}/"
echo "  curl -I https://${API_DOMAIN}/v1/healthcheck"
echo ""
echo "Test renewal:"
echo "  docker compose run --rm --entrypoint certbot certbot renew --dry-run"
