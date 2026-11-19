#!/usr/bin/env bash
#
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
#
# =============================================================================
# FireCalc Docker — Release chain state inspector (read-only)
# =============================================================================
# Mirrors the provenance checks of engine-gate.sh (ADR-0007) WITHOUT publishing
# or mutating anything: branch tips, app release tags, the engine pin chain
# (private build.sbt -> engine-<pin> tag -> VERSIONS.firecalc -> working tree),
# the private checkout pin, and the allowlist cross-check. One PASS/FAIL line
# per check; exit 1 if any check FAILs.
#
# The origin column is best-effort/advisory: `git ls-remote` under a 10s
# timeout with prompting disabled. An unreachable origin prints
# 'origin: unreachable/no-credentials' and never affects the exit code —
# the exit code reflects the local checks only.
#
# Usage: release-state.sh [--private-dir DIR]
#   --private-dir  private firecalc-firebox-austrian-api checkout
#                  (default: <repo-root>/../firecalc-firebox-austrian-api)

set -euo pipefail

# Self-location, hardened against degenerate invocations (e.g. a script fed
# via stdin, where ${BASH_SOURCE[0]} is "bash" and dirname resolves to the
# CWD): if this script's own file is not in the resolved dir, anchor via the
# git toplevel instead of silently computing a wrong path.
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]:-$0}")" && pwd)
_self="$(basename -- "${BASH_SOURCE[0]:-$0}")"
if [[ ! -f "$SCRIPT_DIR/$_self" ]]; then
    echo "warning: release-state.sh invoked without a resolvable script path; anchoring via git toplevel" >&2
    SCRIPT_DIR="$(cd -- "$(git rev-parse --show-toplevel)/firecalc-docker/scripts" && pwd)"
fi
REPO_ROOT=$(cd -- "$SCRIPT_DIR/../.." && pwd)
VERSIONS_FILE="$SCRIPT_DIR/../VERSIONS"
GATE_SH="$SCRIPT_DIR/engine-gate.sh"
PRIVATE_DIR="$REPO_ROOT/../firecalc-firebox-austrian-api"

die() {
    echo "release-state: $*" >&2
    exit 2
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --private-dir)
            [[ $# -ge 2 ]] || die "--private-dir needs an argument"
            PRIVATE_DIR="$2"; shift 2 ;;
        *)
            die "unknown argument: $1 (usage: release-state.sh [--private-dir DIR])" ;;
    esac
done

cd "$REPO_ROOT"

fail_count=0
pass() { echo "PASS $*"; }
fail() { echo "FAIL $*"; fail_count=$((fail_count + 1)); }

# VERSIONS key lookup: `key = value` lines, trimmed, first match wins.
versions_get() {
    sed -n "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*//p" "$VERSIONS_FILE" \
        | head -1 | sed 's/[[:space:]]*$//' || true
}

[[ -f "$VERSIONS_FILE" ]] || die "VERSIONS file not found at $VERSIONS_FILE"
[[ -f "$GATE_SH" ]] || die "engine-gate.sh not found at $GATE_SH"
grep -q '^ENGINE_MODULE_MAIN_DIRS=(' "$GATE_SH" \
    || die "anchor 'ENGINE_MODULE_MAIN_DIRS=(' not found in $GATE_SH"

# The gate's published-module dirs, extracted from engine-gate.sh with the
# same sed-block/grep idiom check-engine-allowlist.sh uses (single source).
mapfile -t ENGINE_DIRS < <(sed -n '/^ENGINE_MODULE_MAIN_DIRS=(/,/^)/p' "$GATE_SH" \
    | grep -oE 'modules/[^[:space:]]+/src/main')
[[ "${#ENGINE_DIRS[@]}" -gt 0 ]] || die "no 'modules/<dir>/src/main' entries parsed from ENGINE_MODULE_MAIN_DIRS in $GATE_SH"

public_pin=$(versions_get firecalc)
private_pin=$(versions_get firecalc-firebox-austrian-api)
[[ -n "$public_pin" ]] || die "VERSIONS has no firecalc pin"
[[ -n "$private_pin" ]] || die "VERSIONS has no firecalc-firebox-austrian-api pin"

# --- 3. engine pin from the private checkout's build.sbt (feeds checks 5/6/8)
pin=""
if [[ -f "$PRIVATE_DIR/build.sbt" ]]; then
    pin=$(grep -oE 'engine_version_pin[[:space:]]*=[[:space:]]*"[^"]*"' "$PRIVATE_DIR/build.sbt" | head -1 | sed 's/.*"\(.*\)".*/\1/' || true)
fi

tag_exists=0 tag_annotated=0 tag_peel=""
if [[ -n "$pin" ]] && git rev-parse --verify -q "refs/tags/engine-$pin^{}" >/dev/null; then
    tag_exists=1
    [[ "$(git cat-file -t "refs/tags/engine-$pin" 2>/dev/null)" = "tag" ]] && tag_annotated=1
    tag_peel="$(git rev-parse "refs/tags/engine-$pin^{}")"
fi

# --- 1. branch tips
declare -A branch_tip=()
for b in dev staging production; do
    branch_tip[$b]=$(git rev-parse --verify -q "refs/heads/$b" || true)
done

# --- best-effort origin (advisory): one batched ls-remote for the relevant refs
origin_ok=0
declare -A origin_sha=()
remote_out=""
if remote_out=$(GIT_TERMINAL_PROMPT=0 timeout 10 git ls-remote origin \
        refs/heads/dev refs/heads/staging refs/heads/production \
        "refs/tags/engine-$pin" "refs/tags/engine-$pin^{}" 2>/dev/null) && [[ -n "$remote_out" ]]; then
    origin_ok=1
    while IFS=$'\t' read -r sha ref; do
        [[ -n "$sha" && -n "$ref" ]] || continue
        # Prefer the peeled line for annotated tags: it names the commit.
        ref="${ref%^{}}"
        origin_sha[$ref]="${sha:0:7}"
    done <<<"$remote_out"
fi
origin_col() { # ref -> suffix for a line, either " origin <sha7>" or an explicit note
    if [[ $origin_ok -eq 0 ]]; then
        echo " [origin: unreachable/no-credentials]"
    elif [[ -n "${origin_sha[$1]:-}" ]]; then
        echo " origin=${origin_sha[$1]}"
    else
        echo " origin: absent"
    fi
}

# --- emit checks, numbered for scan-ability -------------------------------

# 1. branch tips of the release chain
for b in dev staging production; do
    if [[ -n "${branch_tip[$b]}" ]]; then
        pass "1 branch $b tip ${branch_tip[$b]:0:8}$(origin_col "refs/heads/$b")"
    else
        fail "1 branch $b: missing"
    fi
done

# 2. app release tags v<digits>*.{staging,production}.<n>
while read -r t; do
    [[ -n "$t" ]] || continue
    peel=$(git rev-parse -q --verify "refs/tags/$t^{}" || true)
    if [[ -z "$peel" ]]; then
        fail "2 tag $t: cannot peel to a commit"
        continue
    fi
    ann=no; [[ "$(git cat-file -t "refs/tags/$t" 2>/dev/null)" = "tag" ]] && ann=yes
    rel=""
    for b in dev staging production; do
        [[ -n "${branch_tip[$b]}" ]] || continue
        if [[ "${branch_tip[$b]}" = "$peel" ]]; then
            rel+=" $b=equal"
        elif git merge-base --is-ancestor "$peel" "${branch_tip[$b]}" 2>/dev/null; then
            rel+=" $b=descends"
        fi
    done
    if [[ -z "$rel" ]]; then
        fail "2 tag $t annotated=$ann peel=${peel:0:7}: not reachable from dev/staging/production"
    else
        pass "2 tag $t annotated=$ann peel=${peel:0:7} branches:${rel# }"
    fi
done < <(git tag -l | grep -E '^v[0-9].*\.(staging|production)\.[0-9]+$' || true)

# 3. private pin -> annotated engine tag peeling to VERSIONS.firecalc
if [[ -z "$pin" ]]; then
    fail "3 engine pin: could not read engine_version_pin from ${PRIVATE_DIR}/build.sbt"
elif [[ $tag_exists -eq 0 ]]; then
    fail "3 engine pin '$pin': no tag 'engine-$pin' in $(basename "$REPO_ROOT")"
elif [[ $tag_annotated -eq 0 ]]; then
    fail "3 engine pin '$pin': tag 'engine-$pin' is not annotated (ADR-0007 requires git tag -a)"
elif [[ "$tag_peel" = "$public_pin" ]]; then
    pass "3 engine pin '$pin' (private build.sbt): tag 'engine-$pin' annotated, peels to VERSIONS.firecalc$(origin_col "refs/tags/engine-$pin")"
else
    fail "3 engine pin '$pin': tag 'engine-$pin' peels to $tag_peel, VERSIONS.firecalc is $public_pin"
fi

# 4. private checkout HEAD matches its VERSIONS pin
private_head=$(git -C "$PRIVATE_DIR" rev-parse HEAD 2>/dev/null || true)
if [[ -z "$private_head" ]]; then
    fail "4 private checkout: no git HEAD at $PRIVATE_DIR"
elif [[ "$private_head" = "$private_pin" ]]; then
    pass "4 private HEAD ${private_head:0:7} == VERSIONS firecalc-firebox-austrian-api"
else
    fail "4 private HEAD ${private_head:0:7} != VERSIONS firecalc-firebox-austrian-api ${private_pin:0:7}"
fi

# 5/6. working tree vs the engine tag across the published modules
if [[ $tag_exists -eq 1 ]]; then
    if git diff --quiet "refs/tags/engine-$pin^{}" -- "${ENGINE_DIRS[@]}"; then
        pass "5 working tree identical to 'engine-$pin' across ${#ENGINE_DIRS[@]} published module src/main dirs"
    else
        fail "5 working tree differs from tag 'engine-$pin' under the published module dirs"
    fi
    untracked=$(git ls-files --others --exclude-standard -- "${ENGINE_DIRS[@]}" | wc -l)
    if [[ "$untracked" -eq 0 ]]; then
        pass "6 0 untracked files under the published module dirs"
    else
        fail "6 $untracked untracked file(s) under the published module dirs (they would ship)"
    fi
else
    fail "5 not evaluated: 'engine-$pin' unavailable"
    fail "6 not evaluated: 'engine-$pin' unavailable"
fi

# 7. publish allowlists cross-check (build.sbt vs engine-gate.sh)
if allowlist_msg=$(bash "$SCRIPT_DIR/check-engine-allowlist.sh" 2>&1); then
    pass "7 check-engine-allowlist.sh: $allowlist_msg"
else
    fail "7 check-engine-allowlist.sh: $(head -1 <<<"$allowlist_msg")"
fi

# 8. restatement of the pin chain endpoints for scan-ability
if [[ -n "$tag_peel" ]] && [[ "$public_pin" = "$tag_peel" ]]; then
    pass "8 VERSIONS.firecalc $public_pin == peel('engine-$pin') $tag_peel"
else
    fail "8 VERSIONS.firecalc $public_pin != peel('engine-$pin') ${tag_peel:-unresolvable}"
fi

if [[ $fail_count -gt 0 ]]; then
    echo "release-state: $fail_count check(s) FAILED" >&2
    exit 1
fi
echo "release-state: all checks passed"
