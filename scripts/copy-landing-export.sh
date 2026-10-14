#!/usr/bin/env bash
#
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
#
# Copy the Next.js landing export into a UI distribution tree (merged at its
# root, next to the nested app/). The gateway routes /, /(en|fr)/, /_next/
# and /assets/ to the UI upstream, so every part below is required: a
# missing part is fatal, not skipped.
#
# The caller builds the export first (the root Makefile runs 'npm run build',
# deploy.sh runs 'npm ci && npm run build'); this script only copies.
#
# The destination is always the tree's own distribution root,
# <tree-root>/web/dist-app — the same tree the app is built into and the same
# tree Dockerfile.ui copies. There is no second destination to agree with.
#
# Usage:
#   scripts/copy-landing-export.sh <tree-root>
set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "usage: ${0##*/} <tree-root>" >&2
    exit 2
fi

tree_root="$1"
dest="$tree_root/web/dist-app"
out_dir="$tree_root/web/landing/out"

landing_parts=(en fr _next assets 404.html)
for part in "${landing_parts[@]}"; do
    if [[ ! -e "$out_dir/$part" ]]; then
        echo "copy-landing-export: '$part' missing under $out_dir — gateway routes would 404" >&2
        exit 1
    fi
    cp -r "$out_dir/$part" "$dest/"
done
