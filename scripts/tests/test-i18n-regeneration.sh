#!/bin/bash
#
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
#
# Regression guard: editing an i18n `.conf` must regenerate the generated
# translation sources (`…/src_managed/…/files.scala`) from the new content on a
# *one-shot* `sbt --client "<module>/compile"` — no manual nudge, no stale
# artifact, and no `.scala` file touched.
#
# This invariant is a build-graph property, not a library one: sbt 2 caches task
# results by default, and the conf bytes are read inside `FileFunction.cached`,
# so they are not part of any task's input values. Only a real sbt invocation
# can observe the failure, which is why this test drives the build instead of
# asserting in Scala. A one-shot run that replays the cached generator leaves
# `files.scala` stale, compilation stays green, and the breakage surfaces at
# runtime as `Missing key [errors / …]`.
#
# Requires a reachable sbt server (`sbt --client`, e.g. the one Scala Metals
# starts); exits 2 with the sbt output if the preconditions are not met.
#
# Usage:
#   ./scripts/tests/test-i18n-regeneration.sh [module] [sbt-target ...]
#     module: i18n directory name under modules/ (default: i18n)
#     targets: sbt projects to compile. Default for the cross-built i18n module
#              is "<module>" and "<module_underscores>JS". Project ids differ per
#              module (JVM-only, JS-only, or cross) — ask `sbt --client projects`:
#   ./scripts/tests/test-i18n-regeneration.sh ui-i18n ui_i18nJS
#
# This guard drives one-shot builds only. Continuous mode (`~cmd`,
# `make dev-web-ui-compile`) is a different trigger path — its behaviour is the
# `Build triggered by …/fr.conf. Running '<cmd>'.` line documented in
# docs/dev/guides/I18N.md, §"Watching for Changes During Development".
#
# Exit code: 0 all checks passed, 1 a check failed, 2 preconditions unmet.
# The `.conf` is restored on every exit path.

set -u

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$REPO_ROOT" || exit 2

MODULE="${1:-i18n}"
shift 2>/dev/null || true
# sbt compile targets to drive. The default covers the cross-built `i18n` module
# (JVM id `i18n`, Scala.js id `i18nJS`); other modules differ (JVM-only, JS-only,
# or cross) — see the usage block above, and `sbt --client "projects"` for ids.
if [ $# -gt 0 ]; then
    TARGETS=("$@")
else
    TARGETS=("$MODULE" "${MODULE//-/_}JS")
fi
CONF="modules/$MODULE/src/main/resources/i18n/fr.conf"
CANARY_A="# firecalc-i18n-regen-canary-a"
CANARY_B="# firecalc-i18n-regen-canary-b"

PASS=0
FAIL=0
CONF_BACKUP=""

pass() {
    echo "  PASS: $1"
    PASS=$((PASS + 1))
}

fail() {
    echo "  FAIL: $1"
    FAIL=$((FAIL + 1))
}

restore_conf() {
    if [ -n "$CONF_BACKUP" ] && [ -f "$CONF_BACKUP" ]; then
        cp "$CONF_BACKUP" "$CONF"
    fi
}

cleanup() {
    if [ -n "$CONF_BACKUP" ]; then
        restore_conf
        rm -f "$CONF_BACKUP"
        echo "  restored $CONF"
    fi
}
trap cleanup EXIT INT TERM

# Generated payloads for this module, both platforms.
generated_payloads() {
    find target/out -path "*firecalc-$MODULE/src_managed/*files.scala" 2>/dev/null
}

# Payloads whose content matches the marker; with a leading -v, those that do not.
payloads_matching() {
    local invert=""
    if [ "${1:-}" = "-v" ]; then
        invert="yes"
        shift
    fi
    local marker="$1"
    local payload
    while IFS= read -r payload; do
        [ -n "$payload" ] || continue
        if grep -q "$marker" "$payload"; then
            [ -z "$invert" ] && echo "$payload"
        else
            [ -n "$invert" ] && echo "$payload"
        fi
    done < <(generated_payloads)
    return 0
}

compile_all() {
    local target
    for target in "${TARGETS[@]}"; do
        sbt_cmd "$target/compile"
    done
}

sbt_cmd() {
    echo "  \$ sbt --client \"$1\""
    sbt --client "$1" 2>&1 | sed 's/^/    /'
}

echo "=== i18n source-generation regression guard (module: $MODULE) ==="
echo ""

# ─── Preflight ────────────────────────────────────────────────────

if [ ! -f "$CONF" ]; then
    echo "PRECONDITION FAILED: no $CONF"
    exit 2
fi

payloads=$(generated_payloads)
if [ -z "$payloads" ]; then
    echo "PRECONDITION FAILED: no generated payload under"
    echo "  target/out/*/scala-*/firecalc-$MODULE/src_managed/"
    echo "Compile the module once before running this guard."
    exit 2
fi

if ! sbt_cmd "about" >/dev/null; then
    echo "PRECONDITION FAILED: sbt --client is not reachable."
    echo "Start the sbt server (e.g. via Scala Metals, or 'sbt --server --batch')."
    exit 2
fi

# A canary already present means the payload is stale from an earlier run; the
# guard cannot observe regeneration from a state that is already wrong.
if [ -n "$(payloads_matching "$CANARY_A")$(payloads_matching "$CANARY_B")" ]; then
    echo "PRECONDITION FAILED: the generated payload already contains the canary."
    echo "A previous run left a stale artifact; restore it by reverting $CONF"
    echo "and compiling once."
    exit 2
fi

# ─── Check 1: every generated payload tracks the edit ─────────────

echo "Check 1: conf edit regenerates every generated payload"

CONF_BACKUP="$(mktemp)"
cp "$CONF" "$CONF_BACKUP"
printf '\n%s\n' "$CANARY_A" >>"$CONF"

compile_all

missing=$(payloads_matching -v "$CANARY_A")
if [ -z "$missing" ]; then
    pass "every payload ($(generated_payloads | wc -l)) carries the canary after a one-shot compile"
else
    fail "payload(s) still stale — the generator did not re-run:"
    echo "$missing" | sed 's/^/      /'
fi

# ─── Check 2: an unchanged conf does not rewrite the payload ──────

echo ""
echo "Check 2: no-op compile does not rewrite the payload"

before=$(generated_payloads | xargs stat -c '%n %Y' | sort)
sbt_cmd "${TARGETS[0]}/compile" >/dev/null
after=$(generated_payloads | xargs stat -c '%n %Y' | sort)

if [ "$before" = "$after" ] && [ -n "$before" ]; then
    pass "payload mtimes unchanged across a no-op compile (hash short-circuit)"
else
    fail "payload was rewritten although no conf byte changed:"
    diff <(echo "$before") <(echo "$after") | sed 's/^/      /'
fi

# ─── Check 3: a second edit regenerates it again ──────────────────

echo ""
echo "Check 3: a second conf edit regenerates the payload again"

# Revert the conf, then change it differently: the payload must pick up CANARY_B
# and drop CANARY_A. A payload that only ever moves forward (or never moves)
# fails here.
restore_conf
printf '\n%s\n' "$CANARY_B" >>"$CONF"
compile_all >/dev/null

with_b=$(payloads_matching "$CANARY_B")
with_a=$(payloads_matching "$CANARY_A")
if [ -n "$with_b" ] && [ -z "$with_a" ]; then
    pass "payload tracks the second edit and drops the first canary"
else
    fail "payload did not track the second edit (with-B: ${with_b:-none}; with-A-still: ${with_a:-none})"
fi

# ─── Check 4: restoring the conf leaves the payload clean ─────────

echo ""
echo "Check 4: restoring the conf leaves no canary behind"

restore_conf
compile_all >/dev/null

leftover="$(payloads_matching "$CANARY_A")$(payloads_matching "$CANARY_B")"
if [ -z "$leftover" ]; then
    pass "no canary left in any payload (guard is re-runnable)"
else
    fail "canary left in the payload after restoring the conf:"
    echo "$leftover" | sed 's/^/      /'
fi

# ─── Summary ──────────────────────────────────────────────────────

echo ""
echo "─────────────────────────────────"
echo "Results: $PASS passed, $FAIL failed"
echo "─────────────────────────────────"

if [ "$FAIL" -gt 0 ]; then
    exit 1
fi
exit 0
