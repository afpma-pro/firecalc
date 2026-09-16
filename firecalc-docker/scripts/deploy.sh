#!/usr/bin/env bash
#
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
#
# =============================================================================
# FireCalc Docker — Deploy Script
# =============================================================================
# Clones source repositories at pinned refs from VERSIONS, builds artifacts,
# and copies them to the artifacts/ directory for Docker build context.
#
# The VERSIONS file contains lines of the form:
#   <repo-name>=<full-40-char-git-sha>
# Lines starting with # are comments and are skipped.
#
# Expected repos:
#   firecalc                      — public repo (backend + UI)
#   firecalc-firebox-austrian-api — private repo (calc-server)
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
CALC_SERVER="${CALC_SERVER:-}"


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

log() { echo "[deploy] $*"; }
die() { log "FATAL: $*" >&2; exit 1; }

require_cmd() {
    command -v "$1" >/dev/null 2>&1 || die "'$1' is required but not found in PATH"
}

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

# ---------------------------------------------------------------------------
# Clone source repos at pinned refs
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

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

for repo_name in "${!REPO_REFS[@]}"; do
    # Skip private repo clone in Mode A (no calc-server)
    if [[ "$repo_name" == "firecalc-firebox-austrian-api" && "${CALC_SERVER}" != "true" ]]; then
        log "Skipping clone of '$repo_name' (CALC_SERVER is not 'true')"
        continue
    fi
    clone_repo "$repo_name" "${REPO_REFS[$repo_name]}"
done

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

# Find the built JAR (handle varying Scala version in path)
BACKEND_JAR=$(find "$BUILD_DIR/firecalc" -path "*/target/scala-*/firecalc-payments-assembly*.jar" -not -name "*.original" | head -1)
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
# Only build calc-server if CALC_SERVER is set AND the private repo exists
if [[ "${CALC_SERVER}" = "true" && -d "$BUILD_DIR/firecalc-firebox-austrian-api" ]]; then
    log "Building calc-server assembly JAR..."
    (
        cd "$BUILD_DIR/firecalc-firebox-austrian-api"
        sbt "fireboxCalcServer/assembly"
    )

    CALC_JAR=$(find "$BUILD_DIR/firecalc-firebox-austrian-api" -path "*/target/scala-*/firecalc-calc-server-assembly*.jar" -not -name "*.original" | head -1)
    if [[ -n "$CALC_JAR" && -f "$CALC_JAR" ]]; then
        cp "$CALC_JAR" "$ARTIFACTS_DIR/firecalc-calc-server-assembly.jar"
        log "Copied calc-server JAR → artifacts/firecalc-calc-server-assembly.jar"
    else
        die "Calc-server assembly JAR not found after build"
    fi
else
    if [[ "${CALC_SERVER}" != "true" ]]; then
        log "Skipping calc-server build (CALC_SERVER is not 'true')"
    else
        log "Skipping calc-server build (firecalc-firebox-austrian-api not in VERSIONS)"
    fi
fi

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------

log "Artifacts built:"
ls -lh "$ARTIFACTS_DIR/"
log "Deployment build complete."
