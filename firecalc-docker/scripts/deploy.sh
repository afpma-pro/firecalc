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
#   ui-dist/                             — the UI distribution exactly as
#     web/dist-app/ is built: the Vite bundle nested under app/ (the
#     gateway's /app/ locations proxy_pass without a URI rewrite and the
#     inner nginx roots at this tree, so /app/ only resolves when
#     app/index.html is nested) plus the landing export (en/, fr/, _next/,
#     assets/, 404.html) merged at the distribution root. The bundle's API
#     hosts come from
#     modules/ui/.env.<mode>[.local]: the tracked .env.<mode> files hold
#     example.com placeholders on purpose, the gitignored
#     .env.<mode>.local is the deployment config the build host must
#     provide, and the build fails if the bundle still carries a
#     placeholder host.
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$PROJECT_DIR/.." && pwd)"
VERSIONS_FILE="$PROJECT_DIR/VERSIONS"
BUILD_DIR="$PROJECT_DIR/.build"
ARTIFACTS_DIR="$PROJECT_DIR/artifacts"


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

log() { echo "[deploy] $*"; }
die() { log "FATAL: $*" >&2; exit 1; }

require_cmd() {
    command -v "$1" >/dev/null 2>&1 || die "'$1' is required but not found in PATH"
}

# The deployment-environment reader lives in a sibling module; it needs only
# PROJECT_DIR, defined above, and is used by the build-plan resolution below.
source "$SCRIPT_DIR/env.sh"

# Locate a freshly built assembly JAR. The build writes under
# target/out/jvm/scala-<version>/; searching target/ by artifact name keeps
# this independent of that layout while never matching the copies already in
# artifacts/ — in local-tree mode the tree root contains artifacts/, so a
# tree-wide name search would find the previous run's copy.
find_assembly_jar() {
    local tree="$1" jar_name="$2"
    find "$tree/target" -name "$jar_name" -not -name "*.original" 2>/dev/null | head -1
}

# Post-build gate for the UI env carrier decision: the tracked
# modules/ui/.env.<mode> files hold example.com placeholder hosts on purpose,
# and the real deployment values must arrive through the gitignored
# modules/ui/.env.<mode>.local override that Vite merges on top. A bundle still
# containing example.com would ship a placeholder API host behind the gateway,
# so the build fails instead. The mode is passed in from the single resolution
# in the build plan; it is never re-derived here.
assert_ui_bundle_real_hosts() { # <app-dir> <vite-mode>
    local app_dir="$1" vite_mode="$2"
    # The app must be nested: the gateway's /app/ locations proxy_pass with no
    # URI rewrite and the inner nginx serves /app/ from root
    # /usr/share/nginx/html, so /app/ resolves only if index.html is there.
    # Checked here, next to the build that failed to produce it, not at the
    # copy below.
    [[ -f "$app_dir/index.html" ]] \
        || die "UI build produced no ${app_dir#"$BUILD_DIR"/}/index.html — the gateway's /app/ routes would 404"
    local offenders
    offenders="$(grep -rl 'example\.com' "$app_dir" 2>/dev/null | head -1 || true)"
    if [[ -n "$offenders" ]]; then
        die "UI bundle ($vite_mode mode) still carries the example.com placeholder host (first match: ${offenders//$'\n'/ })." \
            "Real deployment values must come from modules/ui/.env.$vite_mode.local, which is gitignored: a build tree without that file always fails here."
    fi
    log "UI bundle proven: app/index.html present, no example.com placeholder host"
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

# Build plan: the deployment environment is read exactly once, here, after
# VERSIONS parsing; every later branch consults these variables, never the
# environment. An exported value always wins; otherwise the value comes from
# .env (see deployment_env).
#
# CALC_SERVER (Mode A when unset/empty): no calc-server is built, so the
# Austrian kinds have no calculator anywhere in the deployment — neither in
# the payments backend nor in the browser. See firecalc-docker/DEPLOYMENT.md.
build_calc_server=false
[[ "$(deployment_env CALC_SERVER)" = "true" ]] && build_calc_server=true

# Local-tree mode: link the working trees into .build instead of cloning
# (rsync-based VPS deploy; the build host has no git credentials).
# firebox_local_dir is the firecalc-firebox-austrian-api checkout, required
# in local-tree mode when CALC_SERVER=true.
build_local_tree=false
[[ "$(deployment_env FIRECALC_BUILD_LOCAL)" = "true" ]] && build_local_tree=true
firebox_local_dir="$(deployment_env FIREBOX_LOCAL_DIR)"

# Vite build mode + UI version suffix, from FIRECALC_ENV. An unrecognised value
# is a configuration error: silently building a dev bundle would put a `-dev`
# version stamp and dev hosts on a staging or production deployment. The mode is
# the model: the npm script name below and the carrier filename in
# assert_ui_bundle_real_hosts are both derived from it, never re-derived there.
# The UI env files are read by Vite from modules/ui in the tree: the tracked
# .env.<mode> holds example.com placeholders, the gitignored .env.<mode>.local
# is the deployment config, and assert_ui_bundle_real_hosts below proves the
# real hosts landed in the bundle.
case "$(deployment_env FIRECALC_ENV)" in
    staging)
        vite_mode="staging"
        ui_version_suffix="staging"
        ;;
    production)
        vite_mode="production"
        ui_version_suffix=""
        ;;
    ""|dev|development)
        vite_mode="development"
        ui_version_suffix="dev"
        ;;
    *)
        die "Unknown FIRECALC_ENV value: expected development, staging or production (set it in the environment or in $PROJECT_DIR/.env)"
        ;;
esac
vite_build_script="build"
[[ "$vite_mode" = "development" ]] || vite_build_script="build:$vite_mode"

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

if [[ "$build_local_tree" = "true" ]]; then
    # The public tree is the repository containing this script: the rsync flow
    # carries the whole repo (.git and tags included) to the build host.
    link_local_tree firecalc "$PROJECT_DIR/.."

    if [[ "$build_calc_server" = "true" ]]; then
        [[ -n "$firebox_local_dir" ]] || die "FIRECALC_BUILD_LOCAL=true with CALC_SERVER=true requires FIREBOX_LOCAL_DIR=<path of the firecalc-firebox-austrian-api checkout>"
        link_local_tree firecalc-firebox-austrian-api "$firebox_local_dir"

        # The private tree is built as-is, so its HEAD must be exactly the
        # pinned stamp (clone mode instead forces the checkout to the pin).
        private_pin="${REPO_REFS[firecalc-firebox-austrian-api]:-}"
        [[ -n "$private_pin" ]] || die "VERSIONS has no firecalc-firebox-austrian-api pin to verify the private checkout against"
        private_head="$(git -C "$firebox_local_dir" rev-parse HEAD)"
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
# The Vite build needs two generated inputs: modules/ui/firecalc-ui.js
# (version constants — vite.config.js:198, index.html:91) and the Scala.js
# artifact from ui/fullLinkJS. The viz/graph bundles are sbt resources, so
# they are built before the Scala.js link. Vite reads its env files from
# modules/ui in the tree itself: the tracked .env.<mode> holds example.com
# placeholders on purpose, and the gitignored .env.<mode>.local is the
# deployment config the build host must provide; assert_ui_bundle_real_hosts
# below fails the build if the bundle still carries a placeholder.
dist_app="$BUILD_DIR/firecalc/web/dist-app"
log "Building UI frontend (npm run $vite_build_script)..."
"$REPO_ROOT/scripts/generate-ui-version.sh" "$BUILD_DIR/firecalc" "$ui_version_suffix"
(
    cd "$BUILD_DIR/firecalc/modules/ui"
    npm ci
    npm run build:viz
    npm run build:graph
    cd "$BUILD_DIR/firecalc"
    sbt "ui/fullLinkJS"
    cd modules/ui
    npm run "$vite_build_script"
)

assert_ui_bundle_real_hosts "$dist_app/app" "$vite_mode"

# --- Landing page (the gateway routes /, /(en|fr)/, /_next/, /assets/ here) ---
# Build in the tree, then merge the export into web/dist-app/ at its root:
# the single copy below is what Dockerfile.ui places at the ui_upstream
# root, and the gateway's /assets/ and /(en|fr)/ locations read the parts
# from there. A missing export part is fatal — those routes would 404.
log "Building landing page..."
(
    cd "$BUILD_DIR/firecalc/web/landing"
    npm ci
    npm run build
)
"$REPO_ROOT/scripts/copy-landing-export.sh" "$BUILD_DIR/firecalc"

# ONE copy of the whole distribution, the app left nested under app/:
# assert_ui_bundle_real_hosts proved app/index.html exists, and the landing
# export was merged into the same tree. Dockerfile.ui copies this directory to
# the ui_upstream root, where the gateway's /app/, /assets/ and /(en|fr)/
# locations read it. Flattening app/ to the ui-dist root would 404 /app/ and
# collide the app's assets/ with the landing's.
rm -rf "$ARTIFACTS_DIR/ui-dist"
cp -r "$dist_app/." "$ARTIFACTS_DIR/ui-dist/"
log "Copied UI distribution (app/ + landing export) → artifacts/ui-dist/"

# --- Logos (required by Typst for PDF generation) ---
# A missing logo is fatal here, not a warning: Dockerfile.backend COPYs both
# files out of artifacts/ unconditionally, so the image build would otherwise
# fail later at that COPY with a less actionable error. logo.png is a
# gitignored operator asset that must be present in the source tree being
# built (a clone at the pinned SHA never contains it); logo.jpg is tracked,
# so its absence means a broken checkout.
log "Copying logo assets..."
for logo_spec in \
    "modules/invoices/configs/logo.png|logo.png is a gitignored operator asset, so a clone at the pinned SHA never contains it: build from the local tree (FIRECALC_BUILD_LOCAL=true) or commit the asset." \
    "modules/reports/src/main/resources/logo.jpg|logo.jpg is tracked at modules/reports/src/main/resources/logo.jpg; its absence means a broken checkout."; do
    logo_rel="${logo_spec%%|*}"
    if [[ -f "$BUILD_DIR/firecalc/$logo_rel" ]]; then
        cp "$BUILD_DIR/firecalc/$logo_rel" "$ARTIFACTS_DIR/"
        log "Copied $(basename "$logo_rel") → artifacts/"
    else
        die "Logo not found at firecalc/$logo_rel — the image build fails at COPY artifacts/$(basename "$logo_rel"). ${logo_spec#*|}"
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
