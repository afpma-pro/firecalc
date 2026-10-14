# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal

# =============================================================================
# FireCalc Nginx — Standalone without Calculation Server
# =============================================================================
# Template for standalone mode without calculation server.
# Domain placeholders are substituted by envsubst at container startup.
# The entrypoint detects no-calc mode and empties calc-* include files.
# Emptying them removes the only calculation seam for Ecolabeled and
# Door15aCatalog: no local engine exists for either, so both are unavailable.
# =============================================================================

worker_processes auto;
user nginx;
error_log /var/log/nginx/error.log warn;
pid /var/run/nginx.pid;

events {
    worker_connections 1024;
}

http {
    include /etc/nginx/conf.d/shared/common.conf;
    include /etc/nginx/conf.d/shared/default-server.conf;
    include /etc/nginx/conf.d/shared/https-servers.conf;
}
