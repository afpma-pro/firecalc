# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal

# =============================================================================
# FireCalc Nginx — Behind Reverse Proxy with Calculation Server
# =============================================================================
# Template for behind-proxy mode with calculation server.
# No domain substitution needed (outer proxy handles domains).
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
}
