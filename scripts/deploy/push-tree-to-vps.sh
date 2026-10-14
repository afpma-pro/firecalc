#!/usr/bin/env bash
#
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
#
# =============================================================================
# Push one working tree to the VPS group folder /home/afpma/data/firecalc-group/
# (VPS_STAGING_REDEPLOY_DEV_PLAN.md ticket 04). This is the single shared
# implementation; the push-repo-*.sh scripts at the repository root are
# config-only shims that supply the source, destination, exclude list, and host:
#
#   scripts/deploy/push-tree-to-vps.sh <SRC_DIR> <DEST_DIR> <EXCLUDE_LIST>
#
# Environment (read here; only the shims supply REMOTE_HOST):
#   REMOTE_HOST  required, no default: the ssh host is machine-specific and
#                belongs in the gitignored shims, never in this tracked file.
#                REMOTE_HOST= (set but empty) means "local destination", no
#                ssh involved.
#   DRY_RUN      non-empty: rsync -n, no mkdir, nothing transferred
#
# The shims are gitignored (.git/info/exclude), so they do not travel with the
# repo. To recreate both in a fresh worktree, copy each body below verbatim
# into the file named by the marker above it (strip the leading "# "; bare "#"
# lines are blank), replacing every <HOST> with your ssh host name, then chmod
# +x both.
#
# --- push-repo-<HOST>.sh -----------------------------------------------------
# #!/usr/bin/env bash
# #
# # SPDX-License-Identifier: AGPL-3.0-or-later
# # Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
# #
#
# # Push the public firecalc working tree to the VPS group folder, which holds
# # both repos side by side (no --delete anywhere: VPS-only state is preserved):
# #
# #   firecalc-group/firecalc                        <- this repo (this script)
# #   firecalc-group/firecalc-firebox-austrian-api   <- private repo
# #                                                     (push-repo-firebox-<HOST>.sh)
# #
# # Config-only shim: the transfer itself lives in
# # scripts/deploy/push-tree-to-vps.sh (shared with the firebox script). This
# # file is gitignored; a fresh worktree recreates it from the copy-pasteable
# # body in that script's usage comment.
# #
# # Dry-run (file set only, nothing transferred):
# #   DRY_RUN=1 ./push-repo-<HOST>.sh
# # Dry-run against a local destination (no SSH needed):
# #   DRY_RUN=1 REMOTE_HOST= REMOTE_DIR=/tmp/group/firecalc ./push-repo-<HOST>.sh
# # Works from any directory (source/exclude paths anchor to this script, not $PWD):
# #   cd firecalc-docker && ../push-repo-<HOST>.sh
#
# set -euo pipefail
#
# SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
#
# # The ssh host is machine-specific: the shared script deliberately has no
# # default for it. REMOTE_HOST= (set but empty) still means "local
# # destination", no ssh involved.
# REMOTE_HOST="${REMOTE_HOST-<HOST>}"
# REMOTE_DIR="${REMOTE_DIR:-/home/afpma/data/firecalc-group/firecalc}"
# EXCLUDE_LIST="${EXCLUDE_LIST:-$SCRIPT_DIR/rsync-<HOST>-exclude-list.txt}"
#
# exec "$SCRIPT_DIR/scripts/deploy/push-tree-to-vps.sh" \
#     "$SCRIPT_DIR" "$REMOTE_DIR" "$EXCLUDE_LIST"
#
# --- push-repo-firebox-<HOST>.sh ---------------------------------------------
# #!/usr/bin/env bash
# #
# # SPDX-License-Identifier: AGPL-3.0-or-later
# # Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
# #
#
# # Push the private firebox repo working tree to the VPS group folder, next to
# # the public tree (see push-repo-<HOST>.sh). The VPS builds the calc
# # server from this tree (FIRECALC_BUILD_LOCAL=true + FIREBOX_LOCAL_DIR point at
# # it), so .git must be included — no --delete anywhere.
# #
# # Config-only shim: the transfer itself lives in
# # scripts/deploy/push-tree-to-vps.sh (shared with the public script). This
# # file is gitignored; a fresh worktree recreates it from the copy-pasteable
# # body in that script's usage comment.
# #
# # Dry-run (file set only, nothing transferred):
# #   DRY_RUN=1 ./push-repo-firebox-<HOST>.sh
# # Dry-run against a local destination (no SSH needed):
# #   DRY_RUN=1 REMOTE_HOST= REMOTE_DIR=/tmp/group/firecalc-firebox-austrian-api ./push-repo-firebox-<HOST>.sh
# # Works from any directory (source/exclude paths anchor to this script, not $PWD):
# #   cd firecalc-docker && ../push-repo-firebox-<HOST>.sh
#
# set -euo pipefail
#
# SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
#
# # The ssh host is machine-specific: the shared script deliberately has no
# # default for it. REMOTE_HOST= (set but empty) still means "local
# # destination", no ssh involved.
# REMOTE_HOST="${REMOTE_HOST-<HOST>}"
#
# # Both repos sit side by side under afpma-pro/; FIREBOX_LOCAL_DIR overrides.
# FIREBOX_LOCAL_DIR="${FIREBOX_LOCAL_DIR:-$SCRIPT_DIR/../firecalc-firebox-austrian-api}"
# REMOTE_DIR="${REMOTE_DIR:-/home/afpma/data/firecalc-group/firecalc-firebox-austrian-api}"
# EXCLUDE_LIST="${EXCLUDE_LIST:-$SCRIPT_DIR/rsync-firebox-<HOST>-exclude-list.txt}"
#
# if [[ ! -d "$FIREBOX_LOCAL_DIR/.git" ]]; then
#     echo "No git checkout at $FIREBOX_LOCAL_DIR (set FIREBOX_LOCAL_DIR)" >&2
#     exit 1
# fi
#
# exec "$SCRIPT_DIR/scripts/deploy/push-tree-to-vps.sh" \
#     "$FIREBOX_LOCAL_DIR" "$REMOTE_DIR" "$EXCLUDE_LIST"
# =============================================================================

set -euo pipefail

if [[ $# -ne 3 ]]; then
    echo "usage: ${0##*/} <SRC_DIR> <DEST_DIR> <EXCLUDE_LIST>" >&2
    exit 2
fi
SRC_DIR="$1"
DEST_DIR="$2"
EXCLUDE_LIST="$3"

# No default on purpose: the ssh host is machine-specific and belongs in the
# gitignored push-repo-*.sh shims, never in this tracked file. `${VAR+set}`
# (not `${VAR:-}`) keeps REMOTE_HOST= (set but empty) working as the
# local-destination override.
if [[ ! ${REMOTE_HOST+set} ]]; then
    echo "error: REMOTE_HOST is unset: set it in the gitignored push-repo-*.sh shim at the repository root, or export REMOTE_HOST= for a local destination" >&2
    exit 2
fi

RSYNC_FLAGS=(-P -avz)
if [[ -n "${DRY_RUN:-}" ]]; then
    RSYNC_FLAGS+=(-n)
fi

# An empty REMOTE_HOST makes the destination a local path (for SSH-free dry-runs).
DEST="${REMOTE_HOST:+${REMOTE_HOST}:}${DEST_DIR}"

# Trailing slash on the source: contents are copied INTO DEST_DIR (created if
# absent), so repeat pushes update in place instead of nesting the tree inside
# itself. rsync only creates the final destination component, so create the
# parent first (skipped in a dry run: no side effects).
if [[ -z "${DRY_RUN:-}" ]]; then
    if [[ -n "$REMOTE_HOST" ]]; then
        ssh "$REMOTE_HOST" mkdir -p "$DEST_DIR"
    else
        mkdir -p "$DEST_DIR"
    fi
fi

rsync "${RSYNC_FLAGS[@]}" -e ssh --exclude-from="$EXCLUDE_LIST" "$SRC_DIR/" "$DEST"
