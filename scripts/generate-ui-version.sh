#!/usr/bin/env bash
#
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
#
# Generate modules/ui/firecalc-ui.js — the version module Vite imports as the
# 'firecalc-ui' input (vite.config.js:198, index.html:91). Shared by the root
# Makefile and firecalc-docker/scripts/deploy.sh so the two build recipes
# cannot drift apart.
#
# Usage:
#   scripts/generate-ui-version.sh <tree-root> <env-suffix>
#
# <env-suffix> is 'dev', 'staging', or empty for production; it selects the
# UI_FULL_VERSION form.
#
# This script is the only reader of the version values. A caller that needs an
# override (the root Makefile, for `make UI_BASE_VERSION=x …`) passes it through
# the UI_BASE_VERSION / ENGINE_VERSION / GIT_HASH environment variables; anything
# left unset is scraped from the tree being built.
set -euo pipefail

if [[ $# -ne 2 ]]; then
    echo "usage: ${0##*/} <tree-root> <env-suffix>" >&2
    exit 2
fi

tree_root="$1"
env_suffix="$2"
ui_base="${UI_BASE_VERSION:-}"
engine_ver="${ENGINE_VERSION:-}"
git_hash="${GIT_HASH:-}"

build_sbt="$tree_root/build.sbt"

# Fill in what the caller did not override, from the tree being built.
# NOTE: scraped by text; must match the lines holding the quoted default
# literals in build.sbt (the publish pin override lives in engine_version,
# not in engine_version_default).
[[ -n "$ui_base" ]] || ui_base="$(grep 'lazy val ui_base_version' "$build_sbt" | sed 's/.*= "\(.*\)".*/\1/' || true)"
[[ -n "$engine_ver" ]] || engine_ver="$(grep 'val engine_version_default' "$build_sbt" | sed 's/.*= "\(.*\)".*/\1/' || true)"
[[ -n "$git_hash" ]] || git_hash="$(git -C "$tree_root" rev-parse --short=8 HEAD || true)"

if [[ -z "$ui_base" || -z "$engine_ver" || -z "$git_hash" ]]; then
    echo "generate-ui-version: could not scrape UI/ENGINE versions from $build_sbt or git HEAD in $tree_root" >&2
    exit 1
fi

# Same UI_FULL_VERSION convention as both callers always produced: the
# environment suffix is only present for dev/staging builds.
full_version="$ui_base${env_suffix:+-$env_suffix}+engine-$engine_ver-$git_hash"

cat > "$tree_root/modules/ui/firecalc-ui.js" <<EOF
export const UI_BASE_VERSION = '$ui_base';
export const ENGINE_VERSION = '$engine_ver';
export const GIT_HASH = '$git_hash';
export const UI_FULL_VERSION = '$full_version';
EOF
