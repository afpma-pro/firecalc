#!/bin/bash
#
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
#
# Drift guard for the push-script shims.
#
# scripts/deploy/push-tree-to-vps.sh embeds the full body of each root
# push-repo-*.sh shim in its usage comment, because those shims are gitignored
# and must be recreatable in a fresh worktree. Nothing else keeps the two
# copies in sync, so this script diffs the embedded body against the live shim
# whenever the live shim exists, and fails loudly on drift.
#
# A shim is named after the ssh host it pushes to, and it is the only file that
# names that host, so no shim name and no host value may be spelled out in a
# tracked file: the helper writes the host as the placeholder <HOST>, both in
# the "# --- push-repo-<HOST>.sh ---" marker and in the body below it. Nothing
# is hard-coded here. For every live push-repo-*.sh in the repository root that
# execs the helper (the legacy push-repo-* scripts predate it and carry their
# own rsync command, so they are left alone) this script recovers the host from
# the shim's own REMOTE_HOST default, substitutes it into each marker to find
# the block that belongs to that shim, and rewrites the host back to <HOST> in
# the live body before diffing.
#
# Usage:
#   ./scripts/tests/test-push-shims.sh
#
# Exit code: 0 if every present shim matches its embedded body, 1 on drift.

set -u

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
HELPER="$REPO_ROOT/scripts/deploy/push-tree-to-vps.sh"

FAIL=0

# The shim names embedded in the helper's usage comment, one per line, exactly
# as they are written there: "<HOST>" stays a placeholder until a live shim
# supplies the host it stands for.
embedded_names() {
    awk '/^# --- / { s = substr($0, 7); sub(/[ -]+$/, "", s); print s }' "$HELPER"
}

# Print the body embedded in the helper's usage comment for one shim name:
# everything between the "# --- <name> ---" marker and the next marker or
# "# =====" closer, with the leading "# " comment prefix stripped and trailing
# blank lines removed.
extract_embedded() {
    awk -v name="$1" '
        index($0, "# --- ") == 1 { inblock = index($0, "# --- " name " ") == 1; next }
        /^# =====/               { inblock = 0 }
        inblock { n++; lines[n] = substr($0, 2); sub(/^ /, "", lines[n]) }
        END {
            while (n > 0 && lines[n] == "") n--
            for (i = 1; i <= n; i++) print lines[i]
        }
    ' "$HELPER"
}

# Print the ssh host a shim defaults REMOTE_HOST to. The helper has no default,
# so a shim that sets none is drift already.
shim_host() {
    local line prefix='REMOTE_HOST="${REMOTE_HOST-' suffix='}"'
    while IFS= read -r line || [[ -n "$line" ]]; do
        case "$line" in
            "$prefix"*"$suffix")
                line="${line#"$prefix"}"
                printf '%s\n' "${line%"$suffix"}"
                return 0
                ;;
        esac
    done < "$1"
    return 1
}
# A shim of the shared helper is the only thing that has an embedded body to
# drift from: it execs the helper instead of carrying its own rsync command.
execs_helper() {
    grep -Eq '^[[:space:]]*exec[[:space:]].*scripts/deploy/push-tree-to-vps\.sh' "$1"
}

# Print the live shim with every occurrence of its host rewritten back to
# <HOST>, so it can be compared byte-for-byte with the embedded body.
normalise_live() {
    local content
    content="$(cat "$1")"
    printf '%s\n' "${content//"$2"/<HOST>}"
}

if [[ ! -f "$HELPER" ]]; then
    echo "FAIL: missing helper $HELPER"
    exit 1
fi

echo "=== push-shim drift check ==="

MARKERS=()
while IFS= read -r name; do
    MARKERS+=("$name")
done < <(embedded_names)

if [[ ${#MARKERS[@]} -eq 0 ]]; then
    echo "FAIL: no embedded shim body found in $HELPER"
    exit 1
fi

MATCHED=()
for i in "${!MARKERS[@]}"; do
    MATCHED+=(0)
done

shopt -s nullglob
SHIMS=("$REPO_ROOT"/push-repo-*.sh)
shopt -u nullglob

for live in "${SHIMS[@]}"; do
    name="${live##*/}"
    # Only the config-only shims of the shared helper have an embedded body to
    # drift from. The legacy push-repo-* scripts in the repository root predate
    # the helper and carry their own rsync command, so they are left alone.
    if ! execs_helper "$live"; then
        continue
    fi
    if ! host="$(shim_host "$live")"; then
        echo "FAIL  $name sets no REMOTE_HOST default (the helper has none; the host belongs in the shim)"
        FAIL=1
        continue
    fi
    idx=-1
    for i in "${!MARKERS[@]}"; do
        if [[ "${MARKERS[i]//<HOST>/$host}" == "$name" ]]; then
            idx=$i
            break
        fi
    done
    if [[ $idx -lt 0 ]]; then
        echo "FAIL  $name has no body embedded in $HELPER"
        FAIL=1
        continue
    fi
    MATCHED[idx]=1
    if diff -u <(extract_embedded "${MARKERS[idx]}") <(normalise_live "$live" "$host") > /tmp/push-shim-drift.diff 2>&1; then
        echo "OK    $name matches its embedded body"
    else
        echo "DRIFT $name differs from the body embedded in the helper:"
        cat /tmp/push-shim-drift.diff
        FAIL=1
    fi
done

for i in "${!MARKERS[@]}"; do
    if [[ "${MATCHED[i]}" -eq 0 ]]; then
        echo "SKIP  ${MARKERS[i]} (not present in this worktree; it is gitignored)"
    fi
done

if [[ "$FAIL" -gt 0 ]]; then
    echo "Update either the shim or the body embedded in $HELPER."
    exit 1
fi
exit 0
