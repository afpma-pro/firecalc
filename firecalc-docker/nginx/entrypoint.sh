#!/bin/sh
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
#
# FireCalc Nginx Entrypoint
#
# Processes the template file and shared configs with envsubst to substitute
# domain placeholders, then starts nginx with the generated config.
# In no-calc mode (FIRECALC_MODE=no-calc), calc-* include files are
# replaced with empty stubs so the unified configs work for both variants.
#
# CORS regex fallback is controlled by envsubst via CORS_REGEX_FALLBACK:
#   - Behind-proxy mode (UI_DOMAIN empty): echoes any origin.
#   - Standalone mode (UI_DOMAIN set): empty, only the UI domain is allowed.
#

# Environment variables:
#   UI_DOMAIN        — UI domain for standalone mode (required for standalone; defaults to empty for behind-proxy)
#   API_DOMAIN       — API domain for standalone mode (required for standalone)
#   NGINX_TEMPLATE   — path to the template file (default: /etc/nginx/nginx.conf.tpl)
set -eu

GENERATED_DIR="/etc/nginx/generated"
GENERATED_SHARED="${GENERATED_DIR}/conf.d/shared"
GENERATED_CONF="${GENERATED_DIR}/nginx.conf"

TEMPLATE="${NGINX_TEMPLATE:-/etc/nginx/nginx.conf.tpl}"

# Create writable directories
mkdir -p "${GENERATED_DIR}/conf.d/shared"

# Copy template to generated location
cp "${TEMPLATE}" "${GENERATED_CONF}"

# Copy shared files to generated location
cp /etc/nginx/conf.d/shared/*.conf "${GENERATED_SHARED}/"

# Detect calc mode from FIRECALC_MODE environment variable
# (set by compose files: "with-calc" or "no-calc")
CALC_MODE="${FIRECALC_MODE:-with-calc}"
if [ "${CALC_MODE}" = "no-calc" ]; then
    CALC_MODE="no"
fi

# CORS regex fallback: enabled in behind-proxy mode (UI_DOMAIN empty),
# disabled in standalone mode (UI_DOMAIN set) to prevent open-origin CORS.
UI_DOMAIN="${UI_DOMAIN:-}"
if [ -z "${UI_DOMAIN}" ]; then
    CORS_REGEX_FALLBACK='~^https?:// $http_origin;'
else
    CORS_REGEX_FALLBACK=''
fi
export CORS_REGEX_FALLBACK

# Catch-all location body: differs by deployment mode.
# In behind-proxy mode (UI_DOMAIN empty), proxy to api_upstream.
# In standalone mode (UI_DOMAIN set), redirect to HTTPS.
if [ -z "${UI_DOMAIN}" ]; then
    CATCH_ALL_ACTION=''
    CATCH_ALL_PROXY='            proxy_pass http://api_upstream;
            proxy_http_version 1.1;
            proxy_set_header Upgrade $http_upgrade;
            proxy_set_header Connection "upgrade";'
else
    CATCH_ALL_ACTION='return 301 https://$host$request_uri;'
    CATCH_ALL_PROXY=''
fi

export CATCH_ALL_ACTION
export CATCH_ALL_PROXY

# Real client IP behind an outer proxy: trusted proxy CIDR for nginx
# set_real_ip_from (see shared/common.conf). Single default covers all four
# templates incl. standalone — an unset var would substitute empty and make
# `set_real_ip_from ;` fail nginx -t. Operators override via compose/.env.
TRUSTED_PROXY_CIDR="${TRUSTED_PROXY_CIDR:-172.16.0.0/12}"
export TRUSTED_PROXY_CIDR

# Run envsubst on the generated config and shared files
# Substitutes ${UI_DOMAIN}, ${API_DOMAIN}, ${CORS_REGEX_FALLBACK}, ${CATCH_ALL_ACTION}, ${CATCH_ALL_PROXY}, ${TRUSTED_PROXY_CIDR}
envsubst '${UI_DOMAIN} ${API_DOMAIN} ${CORS_REGEX_FALLBACK} ${CATCH_ALL_ACTION} ${CATCH_ALL_PROXY} ${TRUSTED_PROXY_CIDR}' < "${GENERATED_CONF}" > "${GENERATED_CONF}.tmp" && mv "${GENERATED_CONF}.tmp" "${GENERATED_CONF}"
for f in "${GENERATED_SHARED}"/*.conf; do
    if [ -f "$f" ]; then
        envsubst '${UI_DOMAIN} ${API_DOMAIN} ${CORS_REGEX_FALLBACK} ${CATCH_ALL_ACTION} ${CATCH_ALL_PROXY} ${TRUSTED_PROXY_CIDR}' < "$f" > "${f}.tmp" && mv "${f}.tmp" "$f"
    fi
done

# In no-calc mode, empty the calc-specific include files so they are
# harmless no-ops when included by the unified configs.
if [ "${CALC_MODE}" = "no" ]; then
    for f in calc-pre-server.conf calc-endpoints.conf calc-healthcheck.conf calc-https-endpoints.conf; do
        if [ -f "${GENERATED_SHARED}/${f}" ]; then
            : > "${GENERATED_SHARED}/${f}"
        fi
    done
fi

# Update include paths to point to generated shared files (in ALL generated files)
for f in "${GENERATED_CONF}" "${GENERATED_SHARED}"/*.conf; do
    sed -i 's|/etc/nginx/conf.d/shared/|/etc/nginx/generated/conf.d/shared/|g' "$f"
done

# Validate generated configuration before starting
nginx -t -c "${GENERATED_CONF}"

# Start nginx
exec nginx -g 'daemon off;'
