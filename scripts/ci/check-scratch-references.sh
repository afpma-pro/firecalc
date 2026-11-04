#!/bin/bash
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
#
# Tracked files must not point into the local issue tracker. The .scratch/ tree is a
# per-task working area that is never committed, so a tracked source file, doc, or ADR
# naming a concrete path inside it carries a reference no fresh clone can follow — and
# the reasoning that lived in that file is lost to everyone who joins later.
#
# Naming the directory itself stays legal, and so do the <feature-slug> placeholders
# AGENTS.md and docs/agents/issue-tracker.md use to describe the convention: only a
# real directory or file name under it is a pointer.
#
# The rule is forward-only: the gates scan what a change touches (--staged, --changed),
# so references that predate it stay put until someone edits that line. --all audits the
# whole tree and lists those legacy pointers.
#
# Usage:
#   ./scripts/ci/check-scratch-references.sh [--staged | --changed [--base-ref REF] | --all]
#     --staged   files staged for commit — the pre-commit gate
#     --changed  files differing from BASE_REF (default: origin/main) — the CI gate
#     --all      every tracked file (audit)
#
# Exits 1 listing the offending file:line, so the reader fixes the reference instead of
# guessing which pointer broke.

set -euo pipefail

MODE="all"
BASE_REF="origin/main"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --all)         MODE="all"; shift ;;
        --staged)      MODE="staged"; shift ;;
        --changed)     MODE="changed"; shift ;;
        --base-ref)    BASE_REF="$2"; shift 2 ;;
        --help|-h)
            sed -n '3,25p' "${BASH_SOURCE[0]}"
            exit 0
            ;;
        *)
            echo "Unknown argument: $1" >&2
            exit 2
            ;;
    esac
done

# `.scratch/` followed by a real name: a pointer into the untracked tree. A trailing
# slash or a `<placeholder>` right after it does not match.
PATTERN='\.scratch/[A-Za-z0-9._-]+'

case "$MODE" in
    all)
        FILES=$(git ls-files)
        ;;
    staged)
        FILES=$(git diff --cached --name-only --diff-filter=ACM)
        ;;
    changed)
        if ! git rev-parse --verify --quiet "$BASE_REF" >/dev/null; then
            echo "Base ref '$BASE_REF' not found; falling back to HEAD." >&2
            BASE_REF="HEAD"
        fi
        FILES=$(git diff --name-only --diff-filter=ACM "$BASE_REF"...HEAD)
        ;;
esac

OFFENCES=""
CHECKED=0
while IFS= read -r file; do
    [[ -z "$file" || ! -f "$file" ]] && continue
    CHECKED=$((CHECKED + 1))
    if HITS=$(grep -nEI -- "$PATTERN" "$file" 2>/dev/null); then
        OFFENCES+=$(printf '%s\n' "$HITS" | sed "s|^|${file}:|")$'\n'
    fi
done <<< "$FILES"

case "$MODE" in
    staged)  SUBJECT="staged files" ;;
    changed) SUBJECT="files changed vs $BASE_REF" ;;
    all)     SUBJECT="tracked files" ;;
esac

if [[ -n "$OFFENCES" ]]; then
    echo ""
    echo "✗ ERROR: $SUBJECT point into the untracked .scratch/ tree:"
    printf '%s\n' "$OFFENCES" | grep -v '^$' | sed 's/^/   /'
    echo ""
    echo "Fix it:"
    echo "  point at something tracked — a symbol, DOM id, message key, ADR or doc path —"
    echo "  or bring the referenced content into the tracked file itself."
    echo ""
    echo "To bypass this check (not recommended):"
    echo "  git commit --no-verify"
    exit 1
fi

echo "✓ No pointer into .scratch/ in $SUBJECT ($CHECKED files checked)"
