# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal

# =============================================================================
# FireCalc Nginx — Standalone with Calculation Server
# =============================================================================
# Template for standalone mode with calculation server.
# Domain placeholders are substituted by envsubst at container startup.
# =============================================================================

include /etc/nginx/conf.d/shared/common.conf;
include /etc/nginx/conf.d/shared/default-server.conf;
include /etc/nginx/conf.d/shared/https-servers.conf;

}
