#!/usr/bin/env bash
#
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
set -euo pipefail

ENV="${1:-}"
[[ -z "$ENV" ]] && { echo "Usage: $0 <development|staging|production>"; exit 1; }

# Validate environment value
case "$ENV" in
  development|staging|production) ;;
  *) echo "ERROR: Invalid environment '$ENV'. Must be development, staging, or production."; exit 1 ;;
esac

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DEFAULTS="$SCRIPT_DIR/.env.defaults"
OVERRIDE="$SCRIPT_DIR/.env.${ENV}"
OUTPUT="$SCRIPT_DIR/.env"

# Validate — fail fast if defaults file doesn't exist
[[ -f "$DEFAULTS" ]] || {
    echo "ERROR: $DEFAULTS not found."
    echo "Copy from template: cp $DEFAULTS.example $DEFAULTS"
    exit 1
}

# Merge: walk defaults in order, substituting override values in-place.
# Override-only keys (no counterpart in defaults) are appended at the end.
# Each key appears exactly once in the output.
FILTERED="$OUTPUT.filtered.tmp"
OVERRIDE_KEYS="$OUTPUT.keys.tmp"

# Arrays to track defaults keys in order
DEFAULT_KEYS=()
declare -A DEFAULT_VALS

# Arrays to track override keys and values
OVERRIDE_KEYS_ARR=()
declare -A OVERRIDE_VALS

# Parse defaults: collect keys in order and their values
while IFS= read -r line || [[ -n "$line" ]]; do
    # Skip comments and blank lines
    [[ "$line" =~ ^[[:space:]]*# ]] && continue
    [[ "$line" =~ ^[[:space:]]*$ ]] && continue
    key="${line%%=*}"
    DEFAULT_KEYS+=("$key")
    DEFAULT_VALS["$key"]="${line#*=}"
done < <(cat "$DEFAULTS")

if [[ -f "$OVERRIDE" ]]; then
    # Parse override: collect keys and their values
    while IFS= read -r line || [[ -n "$line" ]]; do
        [[ "$line" =~ ^[[:space:]]*# ]] && continue
        [[ "$line" =~ ^[[:space:]]*$ ]] && continue
        key="${line%%=*}"
        OVERRIDE_KEYS_ARR+=("$key")
        OVERRIDE_VALS["$key"]="${line#*=}"
    done < <(cat "$OVERRIDE")
fi

# Build output: defaults in order with override values substituted,
# then override-only keys appended at the end
{
    # Pass 1: walk defaults in order, use override value if available
    for key in "${DEFAULT_KEYS[@]}"; do
        if [[ -n "${OVERRIDE_VALS[$key]+x}" ]]; then
            echo "${key}=${OVERRIDE_VALS[$key]}"
        else
            echo "${key}=${DEFAULT_VALS[$key]}"
        fi
    done

    # Pass 2: append override-only keys (no counterpart in defaults)
    if [[ -f "$OVERRIDE" ]]; then
        for key in "${OVERRIDE_KEYS_ARR[@]}"; do
            if [[ -z "${DEFAULT_VALS[$key]+x}" ]]; then
                echo "${key}=${OVERRIDE_VALS[$key]}"
            fi
        done
    fi
} | grep -E '^[A-Za-z_][A-Za-z0-9_]*=' > "$OUTPUT.tmp"
rm -f "$FILTERED" "$OVERRIDE_KEYS"

# Secure before moving into place (avoid secret exposure at default umask)
chmod 600 "$OUTPUT.tmp"
mv "$OUTPUT.tmp" "$OUTPUT"
echo "Generated $OUTPUT (env=$ENV)"
