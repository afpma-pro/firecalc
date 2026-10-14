#!/usr/bin/env bash
#
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
#
# =============================================================================
# FireCalc Docker — Engine publish allowlist cross-check
# =============================================================================
# Verifies that the two engine allowlists stay in sync (ADR-0007):
#   - engine_publish_jvm_ids in <repo-root>/build.sbt — the publish allowlist,
#     the single source of truth;
#   - ENGINE_MODULE_MAIN_DIRS in engine-gate.sh — the provenance gate's module
#     list, which must mirror it. The two lists spell the same modules
#     differently (sbt ids use "_", module directories mostly use "-"), so
#     both sides are normalized to a canonical dash form before comparison.
#
# Standalone, no arguments; resolves paths relative to this script's location.
# Exit 0: the two lists agree as sets (one confirmation line on stdout).
# Exit 1: they differ; the entries missing from each side go to stderr.
# Exit 2: a file or an anchor was not found, or a parsed set is empty —
# never silently pass on empty sets.
# Invoked as the first step of engine_gate_and_publish (engine-gate.sh).

set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
BUILD_SBT="$SCRIPT_DIR/../../build.sbt"
GATE_SH="$SCRIPT_DIR/engine-gate.sh"

die() {
    echo "check-engine-allowlist: $*" >&2
    exit 2
}

[[ -f "$BUILD_SBT" ]] || die "build.sbt not found at $BUILD_SBT"
[[ -f "$GATE_SH" ]] || die "engine-gate.sh not found at $GATE_SH"

grep -q '^lazy val engine_publish_jvm_ids = Seq(' "$BUILD_SBT" \
    || die "anchor 'lazy val engine_publish_jvm_ids = Seq(' not found in $BUILD_SBT"
grep -q '^ENGINE_MODULE_MAIN_DIRS=(' "$GATE_SH" \
    || die "anchor 'ENGINE_MODULE_MAIN_DIRS=(' not found in $GATE_SH"

# sbt project ids from the Seq(...) block, normalized to dash form.
sbt_ids=$(sed -n '/^lazy val engine_publish_jvm_ids = Seq(/,/^)/p' "$BUILD_SBT" \
    | grep -oE '"[A-Za-z0-9_]+"' | tr -d '"' | tr '_' '-' | sort || true)

# Module directory names from the gate's "modules/<dir>/src/main" entries,
# normalized to the same dash form: the real directories are hyphenated
# except for a few underscored ones (modules/firebox_calc_api), so the
# normalization must be applied on both sides, not just the sbt ids.
gate_dirs=$(sed -n '/^ENGINE_MODULE_MAIN_DIRS=(/,/^)/p' "$GATE_SH" \
    | sed -n 's|^[[:space:]]*modules/\([^/[:space:]]*\)/src/main.*$|\1|p' | tr '_' '-' | sort || true)

[[ -n "$sbt_ids" ]] || die "no quoted ids parsed from the engine_publish_jvm_ids block in $BUILD_SBT"
[[ -n "$gate_dirs" ]] || die "no 'modules/<dir>/src/main' entries parsed from ENGINE_MODULE_MAIN_DIRS in $GATE_SH"

tmp_sbt=$(mktemp) tmp_gate=$(mktemp)
trap 'rm -f "$tmp_sbt" "$tmp_gate"' EXIT
printf '%s\n' "$sbt_ids" > "$tmp_sbt"
printf '%s\n' "$gate_dirs" > "$tmp_gate"

# The dash normalization must not collapse distinct names (a real "foo_bar"
# and a real "foo-bar" module would compare equal): that would hide a
# mismatch instead of reporting it.
collapsed=$( { uniq -d "$tmp_sbt"; uniq -d "$tmp_gate"; } | tr '\n' ' ')
[[ -z "$collapsed" ]] \
    || die "distinct entries collapse to the same normalized name after '_' -> '-': $collapsed; refusing to compare"

if cmp -s "$tmp_sbt" "$tmp_gate"; then
    echo "Engine allowlists agree: $(wc -l < "$tmp_sbt") modules in build.sbt and engine-gate.sh"
    exit 0
fi

{
    echo "Engine publish allowlists are out of sync (build.sbt vs engine-gate.sh):"
    only_sbt=$(comm -23 "$tmp_sbt" "$tmp_gate")
    only_gate=$(comm -13 "$tmp_sbt" "$tmp_gate")
    if [[ -n "$only_sbt" ]]; then
        echo "  published by build.sbt but NOT gated by engine-gate.sh:"
        sed 's/^/    /' <<<"$only_sbt"
    fi
    if [[ -n "$only_gate" ]]; then
        echo "  gated by engine-gate.sh but NOT published by build.sbt:"
        sed 's/^/    /' <<<"$only_gate"
    fi
} >&2
exit 1
