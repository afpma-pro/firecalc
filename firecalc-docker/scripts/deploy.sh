#!/usr/bin/env bash
#
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
#
# =============================================================================
# FireCalc Docker — Deploy Script
# =============================================================================
# Builds artifacts from the source repositories and copies them to the
# artifacts/ directory for Docker build context.
#
# Source-tree modes:
#   clone mode (default) — every repo listed in VERSIONS is cloned from
#     GitHub at its pinned SHA into .build/.
#   local-tree mode (FIRECALC_BUILD_LOCAL=true) — no clone, no git
#     credentials: .build/firecalc links to the repository holding this
#     script and .build/firecalc-firebox-austrian-api links to
#     $FIREBOX_LOCAL_DIR (both trees rsynced to the build host, .git and
#     tags included). The private checkout's HEAD must equal its VERSIONS
#     pin; the engine provenance gate then proves this tree matches the
#     tagged engine source named by VERSIONS.firecalc before publishing.
#
# The VERSIONS file contains lines of the form:
#   <repo-name>=<full-40-char-git-sha>
# Lines starting with # are comments and are skipped.
#
# Expected repos:
#   firecalc                      — public repo (backend + UI); its pin names
#     the exact engine stamp source (enforced by the provenance gate)
#   firecalc-firebox-austrian-api — private repo; the calc-server assembly JAR
#     it builds is the only calculation seam for Ecolabeled and Door15aCatalog
#     (no Austrian engine source ships in the public repository)
#
# Build outputs (placed in artifacts/):
#   firecalc-payments-assembly.jar       — backend JAR
#   firecalc-calc-server-assembly.jar    — calc-server JAR
#   logo.png / logo.jpg                  — Typst PDF logos
#   ui-dist/                             — Vite frontend bundle
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
VERSIONS_FILE="$PROJECT_DIR/VERSIONS"
BUILD_DIR="$PROJECT_DIR/.build"
ARTIFACTS_DIR="$PROJECT_DIR/artifacts"
# CALC_SERVER (Mode A when unset/empty): no calc-server is built, so the
# Austrian kinds have no calculator anywhere in the deployment — neither in
# the payments backend nor in the browser. See firecalc-docker/DEPLOYMENT.md.
# The environment is read exactly once, in the build plan after VERSIONS
# parsing; every later branch consults $build_calc_server, never the env.
# Local-tree mode: link the working trees into .build instead of cloning
# (rsync-based VPS deploy; the build host has no git credentials).
FIRECALC_BUILD_LOCAL="${FIRECALC_BUILD_LOCAL:-}"
# Path of the firecalc-firebox-austrian-api checkout (required in local-tree
# mode when CALC_SERVER=true).
FIREBOX_LOCAL_DIR="${FIREBOX_LOCAL_DIR:-}"


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

log() { echo "[deploy] $*"; }
die() { log "FATAL: $*" >&2; exit 1; }

require_cmd() {
    command -v "$1" >/dev/null 2>&1 || die "'$1' is required but not found in PATH"
}

# Locate a freshly built assembly JAR. The build writes under
# target/out/jvm/scala-<version>/; searching target/ by artifact name keeps
# this independent of that layout while never matching the copies already in
# artifacts/ — in local-tree mode the tree root contains artifacts/, so a
# tree-wide name search would find the previous run's copy.
find_assembly_jar() {
    local tree="$1" jar_name="$2"
    find "$tree/target" -name "$jar_name" -not -name "*.original" 2>/dev/null | head -1
}

# The engine provenance gate + publish lives in a sibling module; it uses the
# helpers and paths defined above and is invoked once the source trees exist.
source "$SCRIPT_DIR/engine-gate.sh"

# ---------------------------------------------------------------------------
# Pre-flight checks
# ---------------------------------------------------------------------------

log "Starting deployment build..."

[[ -f "$VERSIONS_FILE" ]] || die "VERSIONS file not found at $VERSIONS_FILE"

require_cmd git
require_cmd sbt
require_cmd node
require_cmd npm

# ---------------------------------------------------------------------------
# Parse VERSIONS file into associative arrays
# ---------------------------------------------------------------------------

declare -A REPO_REFS

while IFS='=' read -r repo_name sha; do
    # Skip comments and blank lines
    [[ -z "$repo_name" || "$repo_name" =~ ^[[:space:]]*# ]] && continue
    # Trim whitespace
    repo_name="$(echo "$repo_name" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
    sha="$(echo "$sha" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
    [[ -n "$repo_name" && -n "$sha" ]] && REPO_REFS["$repo_name"]="$sha"
done < "$VERSIONS_FILE"

[[ ${#REPO_REFS[@]} -gt 0 ]] || die "No valid repo refs found in VERSIONS"

log "Parsed ${#REPO_REFS[@]} repo ref(s) from VERSIONS"

# Build plan: resolve the calc-server decision once, straight from the
# environment; every later branch consults $build_calc_server.
build_calc_server=false
[[ "${CALC_SERVER:-}" = "true" ]] && build_calc_server=true

# ---------------------------------------------------------------------------
# Source trees: clone at pinned refs, or link local checkouts
# ---------------------------------------------------------------------------

clone_repo() {
    local name="$1" sha="$2"
    local dest="$BUILD_DIR/$name"

    log "Cloning '$name' at $sha"
    local url="git@github.com:afpma-pro/${name}.git"
    git clone --depth 1 --single-branch "$url" "$dest"
    (cd "$dest" && git fetch --depth 1 origin "$sha" && git checkout --force "$sha")
    log "Repo '$name' checked out at $(cd "$dest" && git rev-parse --short HEAD)"
}

link_local_tree() {
    local name="$1" src="$2"
    [[ -d "$src/.git" ]] || die "Local-tree mode: '$src' is not a git checkout (needed for '$name')"
    ln -s "$(cd "$src" && pwd)" "$BUILD_DIR/$name"
    log "Linked '$name' → $(cd "$src" && pwd) (local-tree mode, no clone)"
}

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

if [[ "${FIRECALC_BUILD_LOCAL}" = "true" ]]; then
    # The public tree is the repository containing this script: the rsync flow
    # carries the whole repo (.git and tags included) to the build host.
    link_local_tree firecalc "$PROJECT_DIR/.."

    if [[ "$build_calc_server" = "true" ]]; then
        [[ -n "$FIREBOX_LOCAL_DIR" ]] || die "FIRECALC_BUILD_LOCAL=true with CALC_SERVER=true requires FIREBOX_LOCAL_DIR=<path of the firecalc-firebox-austrian-api checkout>"
        link_local_tree firecalc-firebox-austrian-api "$FIREBOX_LOCAL_DIR"

        # The private tree is built as-is, so its HEAD must be exactly the
        # pinned stamp (clone mode instead forces the checkout to the pin).
        private_pin="${REPO_REFS[firecalc-firebox-austrian-api]:-}"
        [[ -n "$private_pin" ]] || die "VERSIONS has no firecalc-firebox-austrian-api pin to verify the private checkout against"
        private_head="$(git -C "$FIREBOX_LOCAL_DIR" rev-parse HEAD)"
        [[ "$private_head" = "$private_pin" ]] || die "Private checkout HEAD ($private_head) does not match VERSIONS.firecalc-firebox-austrian-api ($private_pin): rsync the pinned tree or re-pin VERSIONS"
        log "Private checkout HEAD matches its VERSIONS pin"
    fi
else
    for repo_name in "${!REPO_REFS[@]}"; do
        # Skip private repo clone in Mode A (no calc-server). The private repo only
        # builds the calc-server JAR — the sole Austrian calculation seam — so
        # skipping it leaves Ecolabeled and Door15aCatalog unavailable, never
        # computed locally.
        if [[ "$repo_name" == "firecalc-firebox-austrian-api" && "$build_calc_server" != "true" ]]; then
            log "Skipping clone of '$repo_name' (CALC_SERVER is not 'true')"
            continue
        fi
        clone_repo "$repo_name" "${REPO_REFS[$repo_name]}"
    done
fi

# ---------------------------------------------------------------------------
# Engine provenance gate + publish (CALC_SERVER=true only)
# ---------------------------------------------------------------------------
# The gate itself — allowlist sync check, provenance contract, tag checks,
# publish and verification — lives in engine-gate.sh, sourced from the
# Helpers section above.
if [[ "$build_calc_server" = "true" ]]; then engine_gate_and_publish; fi

# ---------------------------------------------------------------------------
# Build artifacts
# ---------------------------------------------------------------------------

mkdir -p "$ARTIFACTS_DIR"

# --- Backend (payments assembly JAR) ---
log "Building payments backend assembly JAR..."
(
    cd "$BUILD_DIR/firecalc"
    sbt "payments/assembly"
)

BACKEND_JAR=$(find_assembly_jar "$BUILD_DIR/firecalc" "firecalc-payments-assembly*.jar")
[[ -n "$BACKEND_JAR" && -f "$BACKEND_JAR" ]] || die "Backend assembly JAR not found after build"
cp "$BACKEND_JAR" "$ARTIFACTS_DIR/firecalc-payments-assembly.jar"
log "Copied backend JAR → artifacts/firecalc-payments-assembly.jar"

# --- UI (Scala.js + Vite build) ---
log "Building UI frontend..."
(
    cd "$BUILD_DIR/firecalc"
    sbt "ui/fullLinkJS"
    cd modules/ui
    npm ci
    npm run build
)

# Copy the built UI distribution
if [[ -d "$BUILD_DIR/firecalc/web/dist-app/app" ]]; then
    rm -rf "$ARTIFACTS_DIR/ui-dist"
    cp -r "$BUILD_DIR/firecalc/web/dist-app/app" "$ARTIFACTS_DIR/ui-dist"
    log "Copied UI distribution → artifacts/ui-dist/"
else
    die "UI dist-app/app directory not found after build"
fi

# --- Logos (required by Typst for PDF generation) ---
log "Copying logo assets..."
for logo_path in \
    "$BUILD_DIR/firecalc/modules/invoices/configs/logo.png" \
    "$BUILD_DIR/firecalc/modules/reports/src/main/resources/logo.jpg"; do
    if [[ -f "$logo_path" ]]; then
        cp "$logo_path" "$ARTIFACTS_DIR/"
        log "Copied $(basename "$logo_path") → artifacts/"
    else
        log "WARNING: Logo not found at $logo_path — PDF generation may fail"
    fi
done

# --- Calc-server (assembly JAR from private repo) ---
# The source-trees section guarantees the private tree exists whenever
# $build_calc_server is true (clone mode clones it, local-tree mode links
# and verifies it), so no existence probe is needed here. Without the
# calc-server there is no calculation seam for Ecolabeled / Door15aCatalog:
# the payments backend then fails their PDFs with "Austrian firebox detected
# but remote calculation service is not configured" rather than falling back
# to a local engine, which this repository no longer contains.
if [[ "$build_calc_server" = "true" ]]; then
    log "Building calc-server assembly JAR..."
    (
        cd "$BUILD_DIR/firecalc-firebox-austrian-api"
        sbt "firebox_calc_server/assembly"
    )

    CALC_JAR=$(find_assembly_jar "$BUILD_DIR/firecalc-firebox-austrian-api" "firecalc-calc-server-assembly*.jar")
    if [[ -n "$CALC_JAR" && -f "$CALC_JAR" ]]; then
        cp "$CALC_JAR" "$ARTIFACTS_DIR/firecalc-calc-server-assembly.jar"
        log "Copied calc-server JAR → artifacts/firecalc-calc-server-assembly.jar"
    else
        die "Calc-server assembly JAR not found after build"
    fi
else
    log "Skipping calc-server build (CALC_SERVER is not 'true')"
fi

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------

log "Artifacts built:"
ls -lh "$ARTIFACTS_DIR/"
log "Deployment build complete."
