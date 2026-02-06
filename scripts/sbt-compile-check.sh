#!/bin/bash

#
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
#
# Check sbt compilation status from log file.
#
# This script is scope-aware: it reads the compilation scope from
# .logs/sbt-compile.scope (written by sbt-compile-watch.sh) and waits
# for the target module's [FIRECALC_COMPILE_DONE] marker before
# declaring success. This prevents false positives when a dependency
# module succeeds but the target module hasn't compiled yet.
#
# Exit codes:
#   0 - SUCCESS
#   1 - ERROR (compilation failed)
#   2 - IN_PROGRESS (still compiling)
#   3 - NOT_RUNNING (sbt process not found)
#   4 - TIMEOUT (exceeded max wait time)

LOGS_DIR=".logs"
LOG_FILE="$LOGS_DIR/sbt-compile.log"
PID_FILE="$LOGS_DIR/sbt-compile.pid"
SCOPE_FILE="$LOGS_DIR/sbt-compile.scope"

WAIT_MODE=false
ERRORS_ONLY=false

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --wait) WAIT_MODE=true; shift ;;
        --errors) ERRORS_ONLY=true; shift ;;
        *) shift ;;
    esac
done

# Resolve the expected module name from the stored scope.
# Scope "engine" → expected marker "firecalc-engine"
# Scope "" (empty / full project) → expected marker "firecalc-root"
resolve_expected_module() {
    local scope=""
    if [ -f "$SCOPE_FILE" ]; then
        scope=$(cat "$SCOPE_FILE" | tr -d '[:space:]')
    fi

    if [ -z "$scope" ]; then
        echo "firecalc-root"
    else
        echo "firecalc-${scope}"
    fi
}

EXPECTED_MODULE=$(resolve_expected_module)

check_status() {
    # Check if sbt is running
    if [ ! -f "$PID_FILE" ]; then
        echo "STATUS: NOT_RUNNING"
        echo "Run ./scripts/sbt-compile-watch.sh to start"
        return 3
    fi

    PID=$(cat "$PID_FILE")
    if ! kill -0 "$PID" 2>/dev/null; then
        echo "STATUS: NOT_RUNNING"
        echo "Process stopped unexpectedly. Check $LOG_FILE for errors."
        rm "$PID_FILE"
        return 3
    fi

    # Check if log file exists
    if [ ! -f "$LOG_FILE" ]; then
        echo "STATUS: IN_PROGRESS"
        echo "Waiting for compilation to start..."
        return 2
    fi

    # Read the last portion of the log (enough to capture a full cycle)
    local last_lines
    last_lines=$(tail -300 "$LOG_FILE")

    # --- Strategy ---
    # In a compilation cycle, sbt outputs:
    #   1. [info] compiling ...
    #   2. [FIRECALC_COMPILE_DONE] module=<name>  (per completed module)
    #   3. [success] Total time: ...   OR   [error] ...
    #
    # For watch mode, between cycles sbt prints something about
    # monitoring source files. We look at the LATEST cycle only.
    #
    # We need to verify that:
    #   - The target module's marker appeared in the latest cycle
    #   - AND [success] came after it
    # OR:
    #   - [error] lines indicate failure

    # Find the line number of the last [FIRECALC_COMPILE_DONE] for our target module
    local target_marker_line
    target_marker_line=$(echo "$last_lines" | grep -n "\[FIRECALC_COMPILE_DONE\] module=${EXPECTED_MODULE}$" | tail -1 | cut -d: -f1)

    # Find the line number of the last [success] line
    local success_line
    success_line=$(echo "$last_lines" | grep -n "^\[success\] Total time:" | tail -1 | cut -d: -f1)

    # Find the line number of the last [error] line
    local last_error_line
    last_error_line=$(echo "$last_lines" | grep -n "^\[error\]" | tail -1 | cut -d: -f1)

    # Case 1: Target module marker found AND [success] appears after it
    if [ -n "$target_marker_line" ] && [ -n "$success_line" ]; then
        if [ "$success_line" -gt "$target_marker_line" ]; then
            echo "STATUS: SUCCESS"
            if [ "$ERRORS_ONLY" = false ]; then
                echo "$last_lines" | grep "^\[success\] Total time:" | tail -1
            fi
            return 0
        fi
    fi

    # Case 2: Errors detected after target module marker (or if target never appeared)
    # If there are [error] lines and they come after any target marker (or no marker at all)
    if [ -n "$last_error_line" ]; then
        # If target marker exists, only report errors that come after it
        # If no target marker, check if errors come after the last [success]
        # (meaning they are from the current cycle)
        local report_errors=false

        if [ -n "$target_marker_line" ] && [ "$last_error_line" -gt "$target_marker_line" ]; then
            report_errors=true
        elif [ -z "$target_marker_line" ]; then
            # No target marker yet. Check if errors are from the current cycle
            # (after the last [success] or at the end of the log)
            if [ -n "$success_line" ] && [ "$last_error_line" -gt "$success_line" ]; then
                report_errors=true
            elif [ -z "$success_line" ]; then
                # No success line at all — errors are from the current (first) cycle
                report_errors=true
            fi
        fi

        if [ "$report_errors" = true ]; then
            echo "STATUS: ERROR"
            if [ "$ERRORS_ONLY" = false ]; then
                echo "---ERRORS---"
            fi
            # Extract error lines from the current cycle
            # Show errors that appeared after the last success (if any) to avoid old errors
            if [ -n "$success_line" ] && [ -z "$target_marker_line" ]; then
                echo "$last_lines" | tail -n +"$success_line" | grep "^\[error\]"
            elif [ -n "$target_marker_line" ]; then
                echo "$last_lines" | tail -n +"$target_marker_line" | grep "^\[error\]"
            else
                echo "$last_lines" | grep "^\[error\]"
            fi
            return 1
        fi
    fi

    # Case 3: Still compiling
    echo "STATUS: IN_PROGRESS"
    if [ "$ERRORS_ONLY" = false ]; then
        echo "Waiting for module '${EXPECTED_MODULE}' to finish compiling..."
    fi
    return 2
}

if [ "$WAIT_MODE" = true ]; then
    # Exponential backoff: start at 500ms, double each iteration, max 16s per check
    # Total max wait time: 120s (2 minutes) for full project compilation
    DELAY_MS=500
    MAX_DELAY_MS=16000
    TOTAL_WAIT_MS=0
    MAX_TOTAL_WAIT_MS=120000  # 2 minutes

    while true; do
        check_status
        STATUS=$?

        # Exit on success, error, or not running
        if [ $STATUS -eq 0 ] || [ $STATUS -eq 1 ] || [ $STATUS -eq 3 ]; then
            exit $STATUS
        fi

        # Check if we've exceeded max total wait time
        if [ $TOTAL_WAIT_MS -ge $MAX_TOTAL_WAIT_MS ]; then
            echo "STATUS: TIMEOUT"
            echo "Compilation did not complete within 2 minutes"
            exit 4
        fi

        # Sleep with exponential backoff
        sleep $(echo "scale=3; $DELAY_MS/1000" | bc)
        TOTAL_WAIT_MS=$((TOTAL_WAIT_MS + DELAY_MS))

        # Double the delay, cap at max per-check delay
        DELAY_MS=$((DELAY_MS * 2))
        if [ $DELAY_MS -gt $MAX_DELAY_MS ]; then
            DELAY_MS=$MAX_DELAY_MS
        fi
    done
else
    check_status
    exit $?
fi
