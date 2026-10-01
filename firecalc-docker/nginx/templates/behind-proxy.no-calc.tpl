# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal

# =============================================================================
# FireCalc Nginx — Behind Reverse Proxy without Calculation Server
# =============================================================================
# Template for behind-proxy mode without calculation server.
# No domain substitution needed (outer proxy handles domains).
# The entrypoint detects no-calc mode and empties calc-* include files.
# Emptying them removes the only calculation seam for Ecolabeled and
# Door15aCatalog: no local engine exists for either, so both are unavailable.
# =============================================================================

include /etc/nginx/conf.d/shared/common.conf;
include /etc/nginx/conf.d/shared/default-server.conf;

}
